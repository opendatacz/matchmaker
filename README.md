# matchmaker

Services for matchmaking offers and demands on the web of data.

## Usage

A public demo of the application is available at <http://lod2.vse.cz:8080/matchmaker/>.

## Deployment

The application can be deployed in several ways. Luminus provides a [good overview of the standard deployment options](http://www.luminusweb.net/docs/deployment.md) for Clojure web applications. Typical deployment using [Git](http://git-scm.com/) (`git`), [Leiningen](http://leiningen.org/) (`lein`) and a web app container, such as [Tomcat](http://tomcat.apache.org/) or [Jetty](http://www.eclipse.org/jetty/), may consist of the following steps:

```bash
git clone git@github.com:opendatacz/matchmaker.git
cd matchmaker
lein ring uberwar
cp target/matchmaker*.war /path/to/container/webapps/matchmaker.war
```

Immediately, or after restart of the web app container, the application will be available in the `matchmaker` context (e.g., <http://localhost:8080/matchmaker/>).

Template of the application configuration, which provides default values, can be found in `/resources/config/config-public.edn` (see [here](https://github.com/opendatacz/matchmaker/blob/master/resources/config/config-public.edn)). The configuration file needs to be formatted using [EDN](https://github.com/edn-format/edn). You can copy the template and fill in the required missing values (e.g., SPARQL Update endpoint access credentials). 

The path to the configuration file must be provided by the application host environment as the value of the `MATCHMAKER_CONFIG` environment variable: 

```bash
export MATCHMAKER_CONFIG=/path/to/config.edn
```

For example, if you deploy to Tomcat, you can add `MATCHMAKER_CONFIG` into `$TOMCAT_HOME/bin/setenv.sh` (which may need to be created first). Then, restart Tomcat to put the change into effect. 

A configuration template, which is used to provide default values, can be found in `/resources/config/config-public.edn` (see [here](https://github.com/opendatacz/matchmaker/blob/master/resources/config/config-public.edn)). 

## Acknowledgement

The development of this tool was supported by the [LOD2 project](http://lod2.eu/), which is a large-scale integrating project co-funded by the European Commission within the FP7 Information and Communication Technologies Work Programme (Grant Agreement No. 257943).

## License

Copyright &copy; 2013 Jindřich Mynarz

Distributed under the Eclipse Public License, the same as Clojure.
