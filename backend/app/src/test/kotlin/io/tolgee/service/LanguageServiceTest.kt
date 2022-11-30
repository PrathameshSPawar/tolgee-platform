/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.service

import io.tolgee.AbstractSpringTest
import io.tolgee.development.testDataBuilder.data.LanguagePermissionsTestData
import io.tolgee.development.testDataBuilder.data.MtSettingsTestData
import io.tolgee.development.testDataBuilder.data.TranslationCommentsTestData
import io.tolgee.development.testDataBuilder.data.dataImport.ImportTestData
import io.tolgee.model.enums.ProjectPermissionType
import io.tolgee.testing.assert
import io.tolgee.testing.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
class LanguageServiceTest : AbstractSpringTest() {
  @Test
  @Transactional
  fun `remove of language removes existing language reference from import language`() {
    val testData = ImportTestData()
    testDataService.saveTestData(testData.root)

    var foundImportLanguage = importService.findLanguages(testData.import).first()
    assertThat(foundImportLanguage.existingLanguage!!.id).isEqualTo(testData.english.id)
    languageService.deleteLanguage(testData.german.id)
    entityManager.flush()
    entityManager.clear()
    foundImportLanguage = importService.findLanguages(testData.import).find { it.name == "de" }!!
    assertThat(foundImportLanguage.existingLanguage).isEqualTo(null)
  }

  @Test
  @Transactional
  fun `deletes language with MT Service Config`() {
    val testData = MtSettingsTestData()
    testDataService.saveTestData(testData.root)
    entityManager.flush()
    languageService.deleteLanguage(testData.germanLanguage.id)
    languageService.find(testData.germanLanguage.id).assert.isNull()
  }

  @Test
  @Transactional
  fun `deletes language with Comments`() {
    val testData = TranslationCommentsTestData()
    testDataService.saveTestData(testData.root)
    entityManager.flush()
    languageService.deleteLanguage(testData.englishLanguage.id)
    languageService.find(testData.englishLanguage.id).assert.isNull()
  }

  @Test
  @Transactional
  fun `sets view permission when deleted only permitted language`() {
    val testData = LanguagePermissionsTestData()
    testData.project.baseLanguage = testData.germanLanguage
    testDataService.saveTestData(testData.root)
    entityManager.flush()

    languageService.deleteLanguage(testData.englishLanguage.id)
    languageService.find(testData.englishLanguage.id).assert.isNull()

    val enUserData = permissionService.getProjectPermissionData(testData.project.id, testData.enOnlyUser.id)
    assertThat(enUserData.computedPermissions.scopes)
      .containsAll(ProjectPermissionType.VIEW.availableScopes.toList())

    val allLangUserData = permissionService.getProjectPermissionData(testData.project.id, testData.allLangUser.id)
    assertThat(allLangUserData.computedPermissions.scopes)
      .containsAll(ProjectPermissionType.TRANSLATE.availableScopes.toList())

    val explicitBothLangsUserData = permissionService.getProjectPermissionData(
      testData.project.id,
      testData.bothLangsExplicitUser.id
    )
    assertThat(explicitBothLangsUserData.computedPermissions.scopes)
      .containsAll(ProjectPermissionType.TRANSLATE.availableScopes.toList())
    assertThat(explicitBothLangsUserData.computedPermissions.translateLanguageIds).hasSize(1)
  }
}
