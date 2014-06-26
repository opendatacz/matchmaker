# matchmaker

Services for matchmaking offers and demands on the web of data.

## Usage

FIXME

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

## Acknowledgement

The development of this tool was supported by the [LOD2 project](http://lod2.eu/), which is a large-scale integrating project co-funded by the European Commission within the FP7 Information and Communication Technologies Work Programme (Grant Agreement No. 257943).

## License

Copyright &copy; 2013 Jindřich Mynarz

Distributed under the Eclipse Public License, the same as Clojure.
