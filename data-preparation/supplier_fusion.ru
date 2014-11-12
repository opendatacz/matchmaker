DEFINE sql:log-enable 2

PREFIX owl: <http://www.w3.org/2002/07/owl#>

DELETE {
  GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25/supplier-deduplication> {
    ?member ?outP ?outO .
    ?inS ?inP ?member .
  }
}
INSERT {
  GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25/supplier-deduplication> {
    ?cluster ?outP ?outO .
    ?inS ?inP ?cluster .
  }
}
WHERE {
  GRAPH <http://linked.opendata.cz/resource/dataset/VVZ-suppliers-links/2014-11-04> {
    ?cluster owl:sameAs ?member .
  }
  GRAPH <http://linked.opendata.cz/resource/dataset/vestnikverejnychzakazek.cz/2014-08-25/supplier-deduplication> {
    {
      ?member ?outP ?outO .
    } UNION {
      ?inS ?inP ?member .
    }
  }
}
