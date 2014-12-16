DEFINE sql:log-enable 2

PREFIX pc: <http://purl.org/procurement/public-contracts#>

WITH <http://data.i2g.pl/resource/dataset/bzp/2013>
DELETE {
  ?bidder pc:supplierFor ?tender .
}
INSERT {
  ?tender pc:bidder ?bidder .
}
WHERE {
  ?bidder pc:supplierFor ?tender .
}
