
#### アプリケーションの実装

- データベースアクセスするアプリケーションの実装

Spring Bootを使って、データベースにアクセスし、ユーザーデータおよびクレデンシャルをAPIレスポンスとして返すアプリケーションを実装する。なお、データベースアクセスはSpring Data JPAを使用する。開発端末ではHSQLで実行し、Kubenetes環境において、PostgreSQLで実行するようプロファイルを設定する。なお、本アプリケーションは2021年に実装したため、Spring Boot 2.5.4を使用している。適宜最新のバージョンに置き換えて構築すること。

| 動作対象 | バージョン |
| ---- | ---- |
| Java | 11 |
| Spring Boot | 2.5.4 |


-- pom.xml

Spring MVCを使ったREST APIアプリケーションを実装する。以下のライブラリのDependencyを設定する。

1. spring-boot-starter-web
1. spring-boot-starter-data-jpa
1. springfox-boot-starter

なお、Kubenetes向けコンテナイメージの作成には、[jKube](https://eclipse.dev/jkube/docs/kubernetes-maven-plugin/)を利用する。kubernetes-maven-pluginのHELM Repositoryには、
[「Set up MicroK8s」で記載したChartmuseum](2-set-up-microk8s.md) のIPアドレスを設定すること。


```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.5.4</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>org.debugroom</groupId>
    <artifactId>sample-kubernetes-dbaccess-app</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>sample-kubernetes-dbaccess-app</name>
    <description>Demo project for Spring Boot</description>
    <properties>
        <java.version>11</java.version>
        <springfox-swagger2.version>3.0.0</springfox-swagger2.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-boot-starter</artifactId>
            <version>${springfox-swagger2.version}</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.hsqldb</groupId>
            <artifactId>hsqldb</artifactId>
            <scope>runtime</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.eclipse.jkube</groupId>
                <artifactId>kubernetes-maven-plugin</artifactId>
                <configuration>
                    <helm>
                        <home>http://XXX.XXX.XXX.XXX:8000</home>
                        <sources>http://XXX.XXX.XXX.XXX:8000</sources>
                        <keywords>sample-app</keywords>
                        <maintainers>
                            <maintainer>
                                <name>org.debugroom</name>
                                <email>org.debugroom</email>
                            </maintainer>
                        </maintainers>
                        <stableRepository>
                            <name>sample-chartmuseum-stable-repository</name>
                            <url>http://XXX.XXX.XXX.XXX:8000/api/charts</url>
                            <username>debugroom</username>
                            <password>debugroom</password>
                            <type>CHARTMUSEUM</type>
                        </stableRepository>
                        <snapshotRepository>
                            <name>sample-chartmuseum-snapshot-repository</name>
                            <url>http://XXX.XXX.XXX.XXX:8000//api/charts</url>
                            <username>debugroom</username>
                            <password>debugroom</password>
                            <type>CHARTMUSEUM</type>
                        </snapshotRepository>
                    </helm>
                </configuration>
                <version>1.4.0</version>
            </plugin>
        </plugins>
    </build>

</project>

```

-- 起動・設定クラス

SpringBoot起動・設定クラスとして以下を作成する。

1. org.debugroom.sample.kubernetes.config.App.java
1. org.debugroom.sample.kubernetes.config.MvcConfig.java
1. org.debugroom.sample.kubernetes.config.DomainConfig.java
1. org.debugroom.sample.kubernetes.config.JpaConfig.java
1. org.debugroom.sample.kubernetes.config.Swagger2Config.java
1. org.debugroom.sample.kubernetes.config.LocalConfig.java


Appでは、アプリケーションの起動処理を実行する。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

}
```

MvcConfigでは、SpringMvcの設定および、Web層パッケージのコンポーネントスキャン設定を行う。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ComponentScan("org.debugroom.sample.kubernetes.app.web")
@Configuration
public class MvcConfig implements WebMvcConfigurer {
}

```

DomainConfigでは、ビジネスドメイン層パッケージのコンポーネントスキャン設定を行う。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan("org.debugroom.sample.kubernetes.domain.service")
@Configuration
public class DomainConfig {
}

```

JpaConfigでは、JPAおよびSpring Data JPA、RepositoryおよびEntity関連クラスパッケージのコンポーネントスキャン設定を行う。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "org.debugroom.sample.kubernetes.domain.repository"
)
public class JpaConfig {


    @Autowired
    DataSource dataSource;

    @Bean
    public PlatformTransactionManager transactionManager() throws Exception{
        return new JpaTransactionManager();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {

        JpaVendorAdapter adapter = new HibernateJpaVendorAdapter();

        Properties properties = new Properties();
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.format_sql", "true");

        LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean
                = new LocalContainerEntityManagerFactoryBean();
        localContainerEntityManagerFactoryBean.setPackagesToScan(
                "org.debugroom.sample.kubernetes.domain.model.entity");
        localContainerEntityManagerFactoryBean.setJpaProperties(properties);
        localContainerEntityManagerFactoryBean.setJpaVendorAdapter(adapter);
        localContainerEntityManagerFactoryBean.setDataSource(dataSource);

        return localContainerEntityManagerFactoryBean;

    }

}

```

LocalConfigでは、HSQLをデータベースとして使用する設定およびプロファイル設定を行う。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

@Profile("local")
@Configuration
public class LocalConfig {

    @Bean
    public DataSource dataSource(){
        return (new EmbeddedDatabaseBuilder())
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:schema-hsql.sql")
                .addScript("classpath:data-hsql.sql")
                .build();
    }

}
```

**NOTE:** MicroK8s環境では、PostgreSQLデータベースに接続するようプロファイルを環境変数およびapplication-xxx.ymlで切り替えて有効化する。

デフォルト：application.yml

```yaml
spring:
  profiles:
    active: local
```

プロファイル dev : application-dev.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_URL}/sample_database
    username: postgres
    password: postgres
  sql:
    init:
      mode: always
      schema-locations: classpath*:/schema-postgresql.sql
      data-locations: classpath*:/data-postgresql.sql
```

Swagger2ConfigではAPIドキュメントの自動生成を行うよう、ControllerクラスのあるWeb層パッケージを指定する。

```java
package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class Swagger2Config {

    @Bean
    public Docket swagger(){
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.debugroom.sample.kubernetes.app.web"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo());

    }

    private ApiInfo apiInfo(){
        return new ApiInfoBuilder()
                .title("Sample API")
                .description("Sample API description")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact("org.debugroom", "", ""))
                .license("Apache License 2.0")
                .build();
    }
}

```

-- Web層むけSpring MVC関連クラス

Web層むけSpring MVC関連クラスとして以下を作成する。なお、EntityクラスとDTOの変換のマッパークラスを適宜作成するが、ここでは変換のマッパークラスの説明は省略する。

1. org.debugroom.sample.kubernetes.app.web.SampleController.java

SampleControllerでは、以下のREST APIを定義し、必要に応じてドメイン層のサービスクラスを呼び出す。

```java
package org.debugroom.sample.kubernetes.app.web;

import io.swagger.annotations.ApiOperation;
import org.debugroom.sample.kubernetes.app.model.UserResource;
import org.debugroom.sample.kubernetes.app.model.UserResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.debugroom.sample.kubernetes.app.model.Sample;
import org.debugroom.sample.kubernetes.domain.service.SampleService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/sample-api")
public class SampleController {

    @Autowired
    SampleService sampleService;

    @GetMapping("/samples/{id}")
    @ApiOperation("Get Sample resources by id.")
    public Sample getSample(@PathVariable String id){
        return Sample.builder()
                .text(id)
                .build();
    }

    @GetMapping("/samples")
    public List<Sample> getSamples(){
        return Arrays.asList(Sample.builder().text("0").build());
    }

    @GetMapping("/users")
    public List<UserResource> getUsers(){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUsers());
    }

    @GetMapping("/users/{id:[0-9]+}")
    public UserResource getUser(@PathVariable Long id){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUser(id));
    }

    @GetMapping("/users/user")
    public UserResource getUserByLoginId(@RequestParam String loginId){
        return UserResourceMapper.mapWithCredential(
                sampleService.getUserByLoginId(loginId));
    }

}

```

-- ドメイン層およびRepository/Entity関連クラス

ドメイン層およびRepository / Entityクラスとして以下を作成する。なお、Serviceのインターフェースクラスや、Entityキークラスは説明を省略する。

1. org.debugroom.sample.kubernetes.domain.service.SampleServiceImpl.java
1. org.debugroom.sample.kubernetes.domain.repository.UserRepository.java
1. org.debugroom.sample.kubernetes.domain.model.entity.User.java
1. org.debugroom.sample.kubernetes.domain.model.entity.Credential.java

SampleServiceImplでは、ControllerからのCRUD要求に応じてデータベースアクセスするRepositoryクラスを呼び出す。

```java
package org.debugroom.sample.kubernetes.domain.service;

import java.util.List;

import org.debugroom.sample.kubernetes.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.debugroom.sample.kubernetes.domain.model.entity.User;

@Service
public class SampleServiceImpl implements SampleService{

    @Autowired
    UserRepository userRepository;

    @Override
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUser(Long id) {
        return userRepository.findByUserId(id);
    }

    @Override
    public User getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId);
    }

}

```

Repositoryクラスでは、データベーステーブルUSRにアクセスする処理を定義する。
なお、必須ではないが、データをLazy Loadではなく、EAGER FETCHで取得するためにJPQLを記載する。

```java

package org.debugroom.sample.kubernetes.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.debugroom.sample.kubernetes.domain.model.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.credentialsByUserId where u.userId = :userId")
    User findByUserId(@Param("userId") long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.credentialsByUserId where u.loginId = :loginId")
    User findByLoginId(@Param("loginId") String loginId);

}
```

エンティティクラスは、USRおよびCRENETIALテーブルに該当する属性および関連を定義する。

```java
package org.debugroom.sample.kubernetes.domain.model.entity;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "usr", schema = "public", catalog="sample")
public class User {

    private long userId;
    private String firstName;
    private String familyName;
    private String loginId;
    private Boolean isLogin;
    private Boolean isAdmin;
    private String imageFilePath;
    private Integer ver;
    private Timestamp lastUpdatedAt;
    private Set<Credential> credentialsByUserId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userId == user.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, firstName, familyName, loginId, isLogin, ver, lastUpdatedAt);
    }

    @Id
    @Column(name = "user_id", nullable = false)
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Basic
    @Column(name = "first_name", nullable = true, length = 512)
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Basic
    @Column(name = "family_name", nullable = true, length = 512)
    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    @Basic
    @Column(name = "login_id", nullable = true, length = 32)
    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    @Basic
    @Column(name = "is_login", nullable = true)
    public Boolean getLogin() {
        return isLogin;
    }

    public void setLogin(Boolean login) {
        isLogin = login;
    }

    @Basic
    @Column(name = "is_admin", nullable = true)
    public Boolean getAdmin() {
        return isAdmin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
    }

    @Basic
    @Column(name = "image_file_path", nullable = true)
    public String getImageFilePath() {
        return imageFilePath;
    }

    public void setImageFilePath(String imageFilePath) {
        this.imageFilePath = imageFilePath;
    }

    @Basic
    @Column(name = "ver", nullable = true)
    @Version
    public Integer getVer() {
        return ver;
    }

    public void setVer(Integer ver) {
        this.ver = ver;
    }

    @Basic
    @Column(name = "last_updated_at", nullable = true)
    public Timestamp getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Timestamp lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    @OneToMany(mappedBy = "usrByUserId", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    public Set<Credential> getCredentialsByUserId() {
        return credentialsByUserId;
    }

    public void setCredentialsByUserId(Set<Credential> credentialsByUserId) {
        this.credentialsByUserId = credentialsByUserId;
    }

}

```

```java
package org.debugroom.sample.kubernetes.domain.model.entity;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Version;
import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@IdClass(CredentialPK.class)
public class Credential {

    private long userId;
    private String credentialType;
    private String credentialKey;
    private Timestamp validDate;
    private Integer ver;
    private User usrByUserId;

    @Id
    @Column(name = "user_id", nullable = false)
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Id
    @Column(name = "credential_type", nullable = false)
    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    @Basic
    @Column(name = "credential_key", nullable = true, length = 255)
    public String getCredentialKey() {
        return credentialKey;
    }

    public void setCredentialKey(String credentialKey) {
        this.credentialKey = credentialKey;
    }

    @Basic
    @Column(name = "valid_date", nullable = true)
    public Timestamp getValidDate() {
        return validDate;
    }

    public void setValidDate(Timestamp validDate) {
        this.validDate = validDate;
    }

    @Basic
    @Column(name = "ver", nullable = true)
    @Version
    public Integer getVer() {
        return ver;
    }

    public void setVer(Integer ver) {
        this.ver = ver;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id",
            nullable = false, insertable = false, updatable = false)
    public User getUsrByUserId() {
        return usrByUserId;
    }

    public void setUsrByUserId(User usrByUserId) {
        this.usrByUserId = usrByUserId;
    }

}
```
Dockerコンテナイメージの作成のために、src/main/jkubeディレクトリにdeployment.ymlを作成する。
記載方法は[jkubeの公式マニュアル](https://eclipse.dev/jkube/docs/kubernetes-maven-plugin/)を参照すること。

deployment.yml

```yml
spec:
  template:
    spec:
      containers:
        - env: # 環境変数設定
          - name: DB_URL
            value: "postgres:5432"
          - name: SPRING_PROFILES_ACTIVE
            value: "dev"
          imagePullPolicy: Always
```

ソースコードを作成した後、コンテナイメージのビルドおよびHelmレポジトリへのプッシュを行う

```bash
mvn k8s:build k8s:helm k8s:push

[INFO] Scanning for projects...
[INFO]
[INFO] ------------< org.debugroom:sample-kubernetes-dbaccess-app >------------
[INFO] Building sample-kubernetes-dbaccess-app 0.0.1-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- kubernetes-maven-plugin:1.4.0:build (default-cli) @ sample-kubernetes-dbaccess-app ---
[WARNING] k8s: Cannot access cluster for detecting mode: Unknown host kubernetes.default.svc: 名前またはサービスが不明です
[INFO] k8s: Running in Kubernetes mode
[INFO] k8s: Building Docker image in Kubernetes mode
[INFO] k8s: Running generator spring-boot
[INFO] k8s: spring-boot: Using Docker image quay.io/jkube/jkube-java-binary-s2i:0.0.9 as base / builder
[INFO] k8s: [debugroom/sample-kubernetes-dbaccess-app:latest] "spring-boot": Created docker-build.tar in 588 milliseconds
[INFO] k8s: [debugroom/sample-kubernetes-dbaccess-app:latest] "spring-boot": Built image sha256:d96ee
[INFO] k8s: [debugroom/sample-kubernetes-dbaccess-app:latest] "spring-boot": Removed old image sha256:7b3a7
[INFO] k8s: [debugroom/sample-kubernetes-dbaccess-app:latest] "spring-boot": Tag with latest
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.966 s
[INFO] Finished at: 2024-07-28T04:52:01+09:00
[INFO] ------------------------------------------------------------------------

[INFO] Scanning for projects...
[INFO]
[INFO] ------------< org.debugroom:sample-kubernetes-dbaccess-app >------------
[INFO] Building sample-kubernetes-dbaccess-app 0.0.1-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- kubernetes-maven-plugin:1.4.0:helm (default-cli) @ sample-kubernetes-dbaccess-app ---
[WARNING] k8s: Cannot access cluster for detecting mode: Unknown host kubernetes.default.svc: 名前またはサービスが不明です
[INFO] k8s: Creating Helm Chart "sample-kubernetes-dbaccess-app" for Kubernetes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.400 s
[INFO] Finished at: 2024-07-28T04:52:34+09:00
[INFO] ------------------------------------------------------------------------

[INFO] Scanning for projects...
[INFO]
[INFO] ------------< org.debugroom:sample-kubernetes-dbaccess-app >------------
[INFO] Building sample-kubernetes-dbaccess-app 0.0.1-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- kubernetes-maven-plugin:1.4.0:helm-push (default-cli) @ sample-kubernetes-dbaccess-app ---
[WARNING] k8s: Cannot access cluster for detecting mode: Unknown host kubernetes.default.svc: 名前またはサービスが不明です
[INFO] k8s: Creating Helm Chart "sample-kubernetes-dbaccess-app" for Kubernetes
[INFO] k8s: Uploading Helm Chart "sample-kubernetes-dbaccess-app" to sample-chartmuseum-snapshot-repository
[INFO] k8s: 201 - {"saved":true}
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  3.070 s
[INFO] Finished at: 2024-07-28T01:49:52+09:00
[INFO] ------------------------------------------------------------------------


2024-07-27T19:53:11.896Z        INFO    [12] Request served     {"path": "/api/charts", "comment": "", "clientIP": "xxx.xxx.xxx.xxx", "method": "POST", "statusCode": 201, "latency": "572.539µs", "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}
2024-07-27T19:53:11.896Z        DEBUG   [12] Event received     {"event": {"repo_name":"","operation_type":0,"chart_version":{"name":"sample-kubernetes-dbaccess-app","home":"http://xxx.xxx.xxx.xxx:8000","sources":["http://xxx.xxx.xxx.xxx:8000"],"version":"0.0.1-SNAPSHOT","description":"Demo project for Spring Boot","keywords":["sample-app"],"maintainers":[{"name":"org.debugroom","email":"org.debugroom"}],"apiVersion":"v1","urls":["charts/sample-kubernetes-dbaccess-app-0.0.1-SNAPSHOT.tgz"],"created":"2024-07-27T19:53:11.896199746Z","digest":"757812d4cd4d1bc92c1fa3c2523a640019017afd677529cd23961acc690daecf"}}, "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}
2024-07-27T19:53:11.896Z        DEBUG   [12] Entry found in cache store {"repo": "", "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}
2024-07-27T19:53:11.896Z        DEBUG   [12] Entry saved in cache store {"repo": "", "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}
2024-07-27T19:53:11.896Z        DEBUG   [12] Event handled successfully {"event": {"repo_name":"","operation_type":0,"chart_version":{"name":"sample-kubernetes-dbaccess-app","home":"http://xxx.xxx.xxx.xxx:8000","sources":["http://xxx.xxx.xxx.xxx:8000"],"version":"0.0.1-SNAPSHOT","description":"Demo project for Spring Boot","keywords":["sample-app"],"maintainers":[{"name":"org.debugroom","email":"org.debugroom"}],"apiVersion":"v1","urls":["charts/sample-kubernetes-dbaccess-app-0.0.1-SNAPSHOT.tgz"],"created":"2024-07-27T19:53:11.896199746Z","digest":"757812d4cd4d1bc92c1fa3c2523a640019017afd677529cd23961acc690daecf"}}, "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}
2024-07-27T19:53:11.896Z        DEBUG   [12] index-cache.yaml saved in storage  {"repo": "", "reqID": "e9e00662-49e6-4f59-9e9d-90145c6523ff"}

```

```java
```

----
[index]

1. [Set up RHEL EC2 instance on AWS](1-set-up-rhel-instance-on-aws.md)
2. [Set up MicroK8s](2-set-up-microk8s.md)
3. [Set up Application Environment](3-set-up-app-env.md)
4. [Implementation of Application](4-implementation-app.md)
5. [Deploy Application](5-deploy-app.md)
6. [Implementation of Service Mesh Application](6-implementation-service-mesh-app.md)
7. [Deploy Service Mesh Application](7-deploy-service-mesh-app.md)
