# armadillo

Armadillo allows you to easily represent your [json-rpc](https://www.jsonrpc.org/) endpoints as regular scala values.
These endpoints can be later turn into a http server via [tapir](https://github.com/softwaremill/tapir) or 
always up-to-date [openRpc](https://open-rpc.org/getting-started) documentation.

## Why another library

We created armadillo because we wanted to have always up-to-date, automatically generated documentation for our api. 
We looked into tapir as we liked the idea of representing endpoints as pure values but since it is build around http protocol it lacked
ability to represent json-rpc routing which from the http perspective is a single dynamic route (the routing is based on the part of the json payload).
See https://github.com/softwaremill/tapir/issues/621 for details.

## How does it work

1. Using armadillo building blocks describe your jsonrpc endpoints
2. Attach server logic to created endpoints descriptions
3. Convert armadillo endpoints to a single tapir endpoint and expose it via one of available http servers
4. Bonus: automatically generate openRpc documentation and expose it under rpc.discover endpoint  

Head over to the [examples](./example) to see armadillo in action!

## Quickstart with sbt
Add the following dependency:

```
"io.iohk.armadillo" %% "armadillo-core" % "0.0.9"
```

## Quickstart with mill
Add the following dependency:

```
ivy"io.iohk.armadillo::armadillo-core::0.0.9"
```


## Modules description

- core - pure definition of armadillo
- json
  - circe - support for circe library
  - json4s - support for json4s library
- server
  - tapir - a server interpreter from armadillo => tapir 
  - fs2 - a server interpreter from armadillo => fs2.pipe
- example - module which pulls all the things together to show the power of armadillo
- openrpc - interpreter to openrpc 
    - model - openrpc structures
    - circe - circe codecs for openrpc structures
    - circeYaml - extension methods to convert openrpc doc into yaml file
- trace4cats - support for tracing library

## Roadmap

- [x] Unify the design, decide whether user logic should accept envelop
- [x] Create openrpc interpreter
- [ ] Create sttp-client interpreter
- [ ] Cross-compile against other scala versions
- [ ] Cross-compile against scalajs
- [ ] Cross-compile against scala native

## Developer notes

Armadillo uses [mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html) as its build tool. 

To import project into intellij idea call `./millw mill.scalalib.GenIdea/idea`. 

If you would like to use bsp instead, call `./millw mill.bsp.BSP/install`.
  
Releases are fully automated using github actions, simply push a new tag to create a new version.
Note that mill will try to use the tag name directly as a maven artifact version.

## Testing

Weaver exposes a JUnit runner, so tests can be run from Intellij, provided you have JUnit plugin enabled.

To run only selected tests, weaver allows you to tag them with: `test("test name".only)`.

## Credits

This library is **heavily** inspired by [tapir](https://github.com/softwaremill/tapir). In fact, it is just a copy-pasted
version of tapir adapted to the json-rpc standard.

Also, big thanks to [Adam Warski](https://github.com/adamw) for reviewing my initial design and patiently answering all of
my questions about design choices he took in tapir.
