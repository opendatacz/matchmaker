DEFINE sql:log-enable 2

PREFIX gr:     <http://purl.org/goodrelations/v1#>
PREFIX pc:     <http://purl.org/procurement/public-contracts#>
PREFIX schema: <http://schema.org/>

WITH <http://linked.opendata.cz/resource/dataset/ted.europa.eu/be-fused/20140901>
DELETE {
  ?s schema:legalName ?legalName . 
}
INSERT {
  ?s gr:legalName ?legalName . 
}
WHERE {
  ?s schema:legalName ?legalName . 
  [] pc:bidder ?s .
}
