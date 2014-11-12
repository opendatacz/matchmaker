PREFIX pc: <http://purl.org/procurement/public-contracts#>

DELETE {
  GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25> {
    ?contract ?p ?o .
  }
}
WHERE {
  {
    SELECT ?contract
    WHERE {
      GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25> {
        ?contract a pc:Contract ;
          pc:awardedTender/pc:bidder ?winner .
        FILTER NOT EXISTS {
          [] pc:lot ?contract .
        }
      }
    }
    GROUP BY ?contract
    HAVING (COUNT(DISTINCT ?winner) > 1)
  }
  GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25> {
    ?contract ?p ?o .
  }
}
