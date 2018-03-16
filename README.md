# Build
```sh
$ sbt pack
```
# Run
```sh
$ target/pack/bin/startServer
```
```sh
$ curl -w '\n' -X POST localhost:4567/create/test 
{
  "result" : "OK"
}
$ curl -w '\n' -X POST localhost:4567/deposit/test --data '{"amount" : 10}'
{
  "result" : "OK"
}
$ curl -w '\n' localhost:4567/amount/test
{
  "result" : 10
}
$ curl -w '\n' -X POST localhost:4567/create/test2
{
  "result" : "OK"
}
$ curl -w '\n' -X POST localhost:4567/transfer/test/test2 --data '{"amount" : 10}'
{
  "result" : "OK"
}
$ curl -w '\n' localhost:4567/amount/test
{
  "result" : 0
}
$ curl -w '\n' localhost:4567/amount/test2
{
  "result" : 10
}
```