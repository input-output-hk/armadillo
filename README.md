# armadillo

This is a mill based project, so to work with IJ you have to generate project files using:
```
./millw mill.scalalib.GenIdea/idea
```


Example request to test the server:
```curl
curl --location --request POST 'localhost:8545/' \
--header 'Content-Type: application/json' \
--data-raw '{
        "jsonrpc":"2.0",
        "method":"eth_getBlockByNumber",
        "params":{
                "blockNumber": 123123,"includeTransactions":"true"
        },
        "id":1
}'
```

## Modules description

- circe - support for circe library
- json4s - support for json4s library
- tapir - main interpreter allowing conversion from armadillo => tapir
- core - pure definition of armadillo
- example - module which pulls all the things together to show the power of armadillo
- trace4cats - support for tracing library


## Design considerations

1. JsonRpcRequest/JsonRpcResponse/JsonRpcErrorResponse
    TBD
2. Double deserialization
    TBD

## Roadmap

- [ ] Unify the design, decide whether user logic should accept envelop
- [ ] Create openrpc interpreter
- [ ] Create sttp-client interpreter
- [ ] Add tests
- [ ] Cross-compile against other scala versions
- [ ] setup CI