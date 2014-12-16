DEFINE sql:log-enable 2

PREFIX pc: <http://purl.org/procurement/public-contracts#>

WITH <http://data.i2g.pl/resource/dataset/bzp/2013>
DELETE {
  ?s pc:mainObject ?oldCpv .
}
INSERT {
  ?s pc:mainObject ?newCpv .
}
WHERE {
  ?s pc:mainObject ?oldCpv .
  # Remove last check digit
  BIND (IRI(CONCAT("http://linked.opendata.cz/resource/cpv-2008/concept/",
            REPLACE(STR(?oldCpv), "^.+(\\d{8})\\d$", "$1"))) AS ?newCpv)
}
