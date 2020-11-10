package com.polygloat.development;

import com.polygloat.constants.ApiScope;
import com.polygloat.dtos.request.LanguageDTO;
import com.polygloat.dtos.request.SignUpDto;
import com.polygloat.exceptions.NotFoundException;
import com.polygloat.model.*;
import com.polygloat.repository.ApiKeyRepository;
import com.polygloat.repository.RepositoryRepository;
import com.polygloat.repository.UserAccountRepository;
import com.polygloat.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DbPopulatorReal {
    public static final String API_KEY = "this_is_dummy_api_key";

    private final EntityManager entityManager;
    private final UserAccountRepository userAccountRepository;
    private final PermissionService permissionService;
    private final UserAccountService userAccountService;
    @Value("${app.initialUsername:admin}")
    String initialUsername;
    @Value("${app.initialPassword:admin}")
    String initialPassword;
    private final LanguageService languageService;
    private final RepositoryRepository repositoryRepository;
    private final ApiKeyRepository apiKeyRepository;

    private Language de;
    private Language en;

    @Transactional
    public void autoPopulate() {
        //do not populate if db is not empty
        if (userAccountRepository.count() == 0) {
            this.populate("Application");
        }
    }

    public UserAccount createUser(String username) {
        return userAccountService.getByUserName(username).orElseGet(() -> {
            SignUpDto signUpDto = new SignUpDto();
            signUpDto.setEmail(username);
            signUpDto.setName(username);
            signUpDto.setPassword(initialPassword);
            return userAccountService.createUser(signUpDto);
        });
    }

    @Transactional
    public Repository createBase(String repositoryName, String username) {

        UserAccount userAccount = createUser(username);

        Repository repository = new Repository();
        repository.setName(repositoryName);
        repository.setCreatedBy(userAccount);

        en = createLanguage("en", repository);
        de = createLanguage("de", repository);

        permissionService.grantFullAccessToRepo(userAccount, repository);

        repositoryRepository.saveAndFlush(repository);
        entityManager.flush();
        entityManager.clear();

        return repository;
    }


    @Transactional
    public Repository createBase(String repositoryName) {
        return createBase(repositoryName, initialUsername);
    }


    @Transactional
    public Repository populate(String repositoryName) {
        return populate(repositoryName, initialUsername);
    }

    @Transactional
    public Repository populate(String repositoryName, String userName) {
        Repository repository = createBase(repositoryName, userName);

        this.createApiKey(repository);

        createTranslation(repository, "Hello world!", "Hallo Welt!", en, de);
        createTranslation(repository, "English text one.", "Deutsch text einz.", en, de);

        createTranslation(repository, "This is translation in home folder",
                "Das ist die Übersetzung im Home-Ordner", en, de);

        createTranslation(repository, "This is translation in news folder",
                "Das ist die Übersetzung im News-Ordner", en, de);
        createTranslation(repository, "This is another translation in news folder",
                "Das ist eine weitere Übersetzung im Nachrichtenordner", en, de);

        createTranslation(repository, "This is standard text somewhere in DOM.",
                "Das ist Standardtext irgendwo im DOM.", en, de);
        createTranslation(repository, "This is another standard text somewhere in DOM.",
                "Das ist ein weiterer Standardtext irgendwo in DOM.", en, de);
        createTranslation(repository, "This is translation retrieved by service.",
                "Dase Übersetzung wird vom Service abgerufen.", en, de);
        createTranslation(repository, "This is textarea with placeholder and value.",
                "Das ist ein Textarea mit Placeholder und Value.", en, de);
        createTranslation(repository, "This is textarea with placeholder.",
                "Das ist ein Textarea mit Placeholder.", en, de);
        createTranslation(repository, "This is input with value.",
                "Das ist ein Input mit value.", en, de);
        createTranslation(repository, "This is input with placeholder.",
                "Das ist ein Input mit Placeholder.", en, de);

        return repository;
    }


    private void createApiKey(Repository repository) {
        Permission permission = repository.getPermissions().stream().findAny().orElseThrow(NotFoundException::new);

        if (apiKeyRepository.findByKey(API_KEY).isEmpty()) {
            ApiKey apiKey = ApiKey.builder()
                    .repository(repository)
                    .key(API_KEY)
                    .userAccount(permission.getUser())
                    .scopes(Set.of(ApiScope.values()))
                    .build();
            apiKeyRepository.save(apiKey);
        }
    }

    private Language createLanguage(String name, Repository repository) {
        return languageService.createLanguage(LanguageDTO.builder().name(name).abbreviation(name).build(), repository);
    }

    private void createTranslation(Repository repository, String english,
                                   String deutsch, Language en, Language de) {


        Source source = Source.builder().name("sampleApp." + english.replace(" ", "_").toLowerCase().replaceAll("\\.+$", ""))
                .repository(repository).build();

        Translation translation = new Translation();
        translation.setLanguage(en);
        translation.setKey(source);
        translation.setText(english);

        source.getTranslations().add(translation);
        source.getTranslations().add(translation);

        entityManager.persist(translation);

        Translation translationDe = new Translation();
        translationDe.setLanguage(de);
        translationDe.setKey(source);
        translationDe.setText(deutsch);

        source.getTranslations().add(translationDe);


        entityManager.persist(translationDe);

        entityManager.persist(source);
        entityManager.flush();
    }


}
