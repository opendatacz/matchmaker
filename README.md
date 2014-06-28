# matchmaker

Services for matchmaking offers and demands on the web of data.

## Usage

## Implementation

### SPARQL queries

- Get SPARQL query template.
- Render the template.
- Execute the query.
- Parse the query results.

## Evaluation

### Criteria

- Proportion of results in which the sought business entity is present in top 10.
- Mean rank of the sought entity.

### Things to take into account

- Data:
  - Size
    - Number of triples
    - Number of links: e.g., try to interlink CPV codes with more (related) links to shorten graph distance for similar concepts
  - Quality 
    - Before/after cleanup, such as deduplication
  - Improvement for business entities that supplied to more public contracts

## Deployment

The application can be deployed in several ways. Luminus provides a [good overview of the standard deployment options](http://www.luminusweb.net/docs/deployment.md) for Clojure web applications. The application expects its host environment to provide `MATCHMAKER_CONFIG` environment variable, which needs to be set to the path to the configuration file:

```bash
export MATCHMAKER_CONFIG=/path/to/config.edn
```

For example, if you deploy to [Tomcat](http://tomcat.apache.org/), you can add `MATCHMAKER_CONFIG` into `$TOMCAT_HOME/bin/setenv.sh` (which may need to be created first). Then, restart Tomcat to put the change into effect. 

The configuration file needs to be formatted using [EDN](https://github.com/edn-format/edn). A configuration template, which is used to provide default values, can be found in `/resources/config/config-public.edn` (see [here](https://github.com/opendatacz/matchmaker/blob/master/resources/config/config-public.edn)). You can copy the template and fill in the required missing values (e.g., SPARQL Update endpoint access credentials). 

## Acknowledgement

The development of this tool was supported by the [LOD2 project](http://lod2.eu/), which is a large-scale integrating project co-funded by the European Commission within the FP7 Information and Communication Technologies Work Programme (Grant Agreement No. 257943).

## License

Copyright &copy; 2013 Jindřich Mynarz

Distributed under the Eclipse Public License, the same as Clojure.
