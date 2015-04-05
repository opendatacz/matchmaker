DEFINE sql:log-enable 2

PREFIX pc:   <http://purl.org/procurement/public-contracts#>

WITH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25>
DELETE {
  ?contract ?objectProperty <http://linked.opendata.cz/resource/cpv-2008/concept/> .
}
WHERE {
  VALUES ?objectProperty {
    pc:mainObject
    pc:additionalObject
  }
  ?contract ?objectProperty <http://linked.opendata.cz/resource/cpv-2008/concept/> .
}
