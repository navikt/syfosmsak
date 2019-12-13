[![Build status](https://github.com/navikt/syfosmsak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmsak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO sykemelding journaling
This is a simple application who takes the sykemelding2013 XML document, generates a PDF and sends it to Joark to
persist it

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Kafka

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project

repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```
`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:
   
```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```
### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmsak .`

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Andreas Nilsen, `andreas.nilsen@nav.no`
* Sebastian Knudsen, `sebastian.knudsen@nav.no`
* Tia Firing, `tia.firing@nav.no`

### For NAV employees
We are available at the Slack channel #team-sykmelding
