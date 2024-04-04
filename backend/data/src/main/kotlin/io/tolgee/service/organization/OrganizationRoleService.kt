package io.tolgee.service.organization

import io.tolgee.constants.Caches
import io.tolgee.constants.Message
import io.tolgee.dtos.cacheable.UserAccountDto
import io.tolgee.dtos.cacheable.UserOrganizationRoleDto
import io.tolgee.dtos.request.organization.SetOrganizationRoleDto
import io.tolgee.dtos.request.validators.exceptions.ValidationException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.exceptions.PermissionException
import io.tolgee.model.Invitation
import io.tolgee.model.Organization
import io.tolgee.model.OrganizationRole
import io.tolgee.model.UserAccount
import io.tolgee.model.enums.OrganizationRoleType
import io.tolgee.repository.OrganizationRepository
import io.tolgee.repository.OrganizationRoleRepository
import io.tolgee.security.authentication.AuthenticationFacade
import io.tolgee.service.security.PermissionService
import io.tolgee.service.security.UserAccountService
import io.tolgee.service.security.UserPreferencesService
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OrganizationRoleService(
  private val organizationRoleRepository: OrganizationRoleRepository,
  private val authenticationFacade: AuthenticationFacade,
  private val userAccountService: UserAccountService,
  @Lazy
  private val permissionService: PermissionService,
  private val organizationRepository: OrganizationRepository,
  @Lazy
  private val userPreferencesService: UserPreferencesService,
  @Suppress("SelfReferenceConstructorParameter") @Lazy
  private val self: OrganizationRoleService,
  private val cacheManager: CacheManager,
) {
  fun canUserViewStrict(
    userId: Long,
    organizationId: Long,
  ) = this.organizationRepository.canUserView(userId, organizationId)

  fun checkUserCanView(organizationId: Long) {
    checkUserCanView(
      authenticationFacade.authenticatedUser.id,
      organizationId,
      authenticationFacade.authenticatedUser.role == UserAccount.Role.ADMIN,
    )
  }

  private fun checkUserCanView(
    userId: Long,
    organizationId: Long,
    isAdmin: Boolean = false,
  ) {
    if (!isAdmin && !canUserViewStrict(userId, organizationId)) throw PermissionException()
  }

  fun canUserView(
    userId: Long,
    organizationId: Long,
  ): Boolean {
    val userAccountDto =
      userAccountService.findDto(userId)
        ?: return false

    return canUserView(userAccountDto, organizationId)
  }

  fun canUserView(
    user: UserAccountDto,
    organizationId: Long,
  ) = user.role === UserAccount.Role.ADMIN || this.organizationRepository.canUserView(user.id, organizationId)

  /**
   * Verifies the user has a role equal or higher than a given role.
   *
   * @param userId The user to check.
   * @param organizationId The organization to check role in.
   * @param role The minimum role the user should have.
   * @return Whether the user has at least the [role] role in the organization.
   */
  fun isUserOfRole(
    userId: Long,
    organizationId: Long,
    role: OrganizationRoleType,
  ): Boolean {
    // The use of a when here is an intentional code design choice.
    // If a new role gets added, this will not compile and will need to be addressed.
    return when (role) {
      OrganizationRoleType.MEMBER ->
        isUserMemberOrOwner(userId, organizationId)

      OrganizationRoleType.OWNER ->
        isUserOwner(userId, organizationId)
    }
  }

  fun checkUserIsOwner(
    userId: Long,
    organizationId: Long,
  ) {
    val isServerAdmin = userAccountService.getDto(userId).role == UserAccount.Role.ADMIN
    if (this.isUserOwner(userId, organizationId) || isServerAdmin) return else throw PermissionException()
  }

  fun checkUserIsOwner(organizationId: Long) {
    this.checkUserIsOwner(authenticationFacade.authenticatedUser.id, organizationId)
  }

  fun checkUserIsMemberOrOwner(
    userId: Long,
    organizationId: Long,
  ) {
    val isServerAdmin = userAccountService.getDto(userId).role == UserAccount.Role.ADMIN
    if (isUserMemberOrOwner(userId, organizationId) || isServerAdmin) {
      return
    }
    throw PermissionException()
  }

  fun isUserMemberOrOwner(
    userId: Long,
    organizationId: Long,
  ): Boolean {
    val role = self.getDto(organizationId, userId).type
    return role != null
  }

  fun isUserOwner(
    userId: Long,
    organizationId: Long,
  ): Boolean {
    val role = self.getDto(organizationId, userId)
    return role.type == OrganizationRoleType.OWNER
  }

  fun find(id: Long): OrganizationRole? {
    return organizationRoleRepository.findById(id).orElse(null)
  }

  fun getType(
    userId: Long,
    organizationId: Long,
  ): OrganizationRoleType {
    return self.findType(userId, organizationId) ?: throw PermissionException()
  }

  fun getType(organizationId: Long): OrganizationRoleType {
    return self.getType(authenticationFacade.authenticatedUser.id, organizationId)
  }

  fun findType(organizationId: Long): OrganizationRoleType? {
    return self.findType(authenticationFacade.authenticatedUser.id, organizationId)
  }

  fun findType(
    userId: Long,
    organizationId: Long,
  ): OrganizationRoleType? {
    return self.getDto(organizationId, userId).type
  }

  @Cacheable(Caches.ORGANIZATION_ROLES, key = "{#organizationId, #userId}")
  fun getDto(
    organizationId: Long,
    userId: Long,
  ): UserOrganizationRoleDto {
    val entity = organizationRoleRepository.findOneByUserIdAndOrganizationId(userId, organizationId)
    return UserOrganizationRoleDto.fromEntity(userId, entity)
  }

  @CacheEvict(Caches.ORGANIZATION_ROLES, key = "{#organization.id, #user.id}")
  fun grantRoleToUser(
    user: UserAccount,
    organization: Organization,
    organizationRoleType: OrganizationRoleType,
  ) {
    OrganizationRole(user = user, organization = organization, type = organizationRoleType)
      .let {
        organization.memberRoles.add(it)
        user.organizationRoles.add(it)
        organizationRoleRepository.save(it)
      }
  }

  fun leave(organizationId: Long) {
    this.removeUser(organizationId, authenticationFacade.authenticatedUser.id)
  }

  fun removeUser(
    organizationId: Long,
    userId: Long,
  ) {
    val role =
      organizationRoleRepository.findOneByUserIdAndOrganizationId(userId, organizationId)?.let {
        organizationRoleRepository.delete(it)
        it
      }
    val permissions = permissionService.removeAllProjectInOrganization(organizationId, userId)

    if (role == null && permissions.isEmpty()) {
      throw NotFoundException(Message.USER_IS_NOT_MEMBER_OF_ORGANIZATION)
    }

    userPreferencesService.refreshPreferredOrganization(userId)
    evictCache(organizationId, userId)
  }

  fun onOrganizationDelete(organization: Organization) {
    organizationRoleRepository.deleteByOrganization(organization)
  }

  fun grantMemberRoleToUser(
    user: UserAccount,
    organization: Organization,
  ) {
    self.grantRoleToUser(user, organization, organizationRoleType = OrganizationRoleType.MEMBER)
  }

  fun grantOwnerRoleToUser(
    user: UserAccount,
    organization: Organization,
  ) {
    self.grantRoleToUser(user, organization, organizationRoleType = OrganizationRoleType.OWNER)
  }

  fun setMemberRole(
    organizationId: Long,
    userId: Long,
    dto: SetOrganizationRoleDto,
  ) {
    val user = userAccountService.findActive(userId) ?: throw NotFoundException()
    organizationRoleRepository.findOneByUserIdAndOrganizationId(user.id, organizationId)?.let {
      it.type = dto.roleType
      organizationRoleRepository.save(it)
    } ?: throw ValidationException(Message.USER_IS_NOT_MEMBER_OF_ORGANIZATION)
    evictCache(organizationId, userId)
  }

  fun createForInvitation(
    invitation: Invitation,
    type: OrganizationRoleType,
    organization: Organization,
  ): OrganizationRole {
    return OrganizationRole(invitation = invitation, type = type, organization = organization).let {
      organizationRoleRepository.save(it)
    }
  }

  fun acceptInvitation(
    organizationRole: OrganizationRole,
    userAccount: UserAccount,
  ) {
    organizationRole.invitation = null
    organizationRole.user = userAccount
    organizationRoleRepository.save(organizationRole)
    // switch user to the organization when accepted invitation
    organizationRole.organization?.let {
      userPreferencesService.setPreferredOrganization(it, userAccount)
      evictCache(it.id, userAccount.id)
    }
  }

  fun isAnotherOwnerInOrganization(id: Long): Boolean {
    return this.organizationRoleRepository
      .countAllByOrganizationIdAndTypeAndUserIdNot(
        id,
        OrganizationRoleType.OWNER,
        authenticationFacade.authenticatedUser.id,
      ) > 0
  }

  fun saveAll(organizationRoles: List<OrganizationRole>) {
    organizationRoleRepository.saveAll(organizationRoles)
    organizationRoles.forEach {
      evictForRole(it)
    }
  }

  private fun evictForRole(it: OrganizationRole) {
    val organizationId = it.organization?.id
    val userId = it.user?.id
    if (organizationId != null && userId != null) {
      evictCache(organizationId, userId)
    }
  }

  fun evictCache(
    organizationId: Long,
    userId: Long,
  ) {
    val cache = cacheManager.getCache(Caches.ORGANIZATION_ROLES)
    cache?.evict(arrayListOf(organizationId, userId))
  }
}
