# madie-fhir-elm-translation

This is a Springboot application to translate CQL to ELM.

Translation service uses madie-rest-commons and madie-server-commons as dependencies, these artifacts are hosted on GitHub packages.


GitHub requires authentication before downloading artifacts, So Add GitHub credentials ( recommended to use GitHub Access Token ).

Add the following server in ./m2/settings.xml
```
  <servers>
    <server>
      <id>github</id>
      <username>Your Github UserName</username>
      <password>Your Github Access Token</password>
    </server>
  </servers>
</settings>
```

To build
```
mvn clean install
```
