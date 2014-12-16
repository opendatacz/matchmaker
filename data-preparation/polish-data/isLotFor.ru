DEFINE sql:log-enable 2

PREFIX pc: <http://purl.org/procurement/public-contracts#>

WITH <http://data.i2g.pl/resource/dataset/bzp/2013>
DELETE {
  ?s pc:isLotFor ?o .
}
INSERT {
  ?o pc:lot ?s .
}
WHERE {
  ?s pc:isLotFor ?o .
}
