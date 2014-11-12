DEFINE sql:log-enable 2

PREFIX pc: <http://purl.org/procurement/public-contracts#>

WITH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25>
DELETE {
  ?s pc:supplier ?o .
}
INSERT {
  ?s pc:bidder ?o .
}
WHERE {
  ?s pc:supplier ?o .
}
