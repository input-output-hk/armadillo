# armadillo

Armadillo allows you to easily represent your json-rpc endpoints as regular scala values.
These endpoints can be later turn into a http server via tapir or always up-to-date [openRpc](https://open-rpc.org/getting-started) documentation.

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
    - circeYaml - extension methos to convert openrpc doc into yaml file
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

## Credits

This library is **heavily** inspired by [tapir](https://github.com/softwaremill/tapir). In fact it is just a copy-pasted
version of tapir adapted to the json-rpc standard.

Also, big thanks to [Adam Warski](https://github.com/adamw) for reviewing my initial design and patiently answering all of
my questions about design choices he took in tapir.
 
