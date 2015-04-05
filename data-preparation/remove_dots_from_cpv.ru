DEFINE sql:log-enable 2

PREFIX pc:   <http://purl.org/procurement/public-contracts#>

WITH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25>
DELETE {
  ?contract ?objectProperty ?cpv . 
}
INSERT {
  ?contract ?objectProperty ?newCpv .
}
WHERE {
  VALUES ?objectProperty {
    pc:mainObject
    pc:additionalObject
  }
  ?contract ?objectProperty ?cpv .
  BIND (SUBSTR(STR(?cpv), 53) AS ?code) 
  FILTER CONTAINS(?code, ".")
  BIND (IRI(CONCAT("http://linked.opendata.cz/resource/cpv-2008/concept/",
                   REPLACE(?code, "\\.", "")))
        AS ?newCpv) 
}
