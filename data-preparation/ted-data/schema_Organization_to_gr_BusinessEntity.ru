DEFINE sql:log-enable 2

PREFIX gr:     <http://purl.org/goodrelations/v1#>
PREFIX pc:     <http://purl.org/procurement/public-contracts#>
PREFIX schema: <http://schema.org/>

WITH <http://linked.opendata.cz/resource/dataset/ted.europa.eu/be-fused/20140901>
DELETE {
  ?s a schema:Organization .
}
INSERT {
  ?s a gr:BusinessEntity .
}
WHERE {
  ?s a schema:Organization .
  [] pc:bidder ?s .
}
