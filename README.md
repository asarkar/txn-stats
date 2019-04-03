[![Build Status](https://api.travis-ci.org/asarkar/txn-stats.svg)](https://travis-ci.org/asarkar/txn-stats)

Transactions statistics
===

We would like to have a RESTful API for our statistics. The main use case for the API is to calculate realtime 
statistics for the last 60 seconds of transactions.

The API needs the following endpoints:

* `POST /transactions` – called every time a transaction is made.
* `GET /statistics` – returns the statistic based of the transactions of the last 60 seconds.
* `DELETE /transactions` – deletes all transactions.
 

You can complete the challenge offline using an IDE of your choice. To download the application skeleton, please 
enable `Use Git` in the editor and follow the instructions on screen. Please make sure you push your changes to the 
master branch and test your solution on HackerRank before submitting.

## Specs
`POST /transactions`

This endpoint is called to create a new transaction. It MUST execute in constant time and memory (O(1)).

Body:
```
{
  "amount": "12.3343",
  "timestamp": "2018-07-17T09:59:51.312Z"
}
```
Where:

* `amount` – transaction amount; a string of arbitrary length that is parsable as a BigDecimal
* `timestamp` – transaction time in the ISO 8601 format `YYYY-MM-ddTHH:mm:ss.SSSZ` in the UTC timezone 
  (this is not the current timestamp)
 

Returns: Empty body with one of the following:

* 201 – in case of success
* 204 – if the transaction is older than 60 seconds
* 400 – if the JSON is invalid
* 422 – if any of the fields are not parsable or the transaction date is in the future
 

`GET /statistics`

This endpoint returns the statistics based on the transactions that happened in the last 60 seconds. It MUST execute 
in constant time and memory (O(1)).

Returns:
```
{
  "sum": "1000.00",
  "avg": "100.53",
  "max": "200000.49",
  "min": "50.23",
  "count": 10
}
```
Where:

* `sum` – a `BigDecimal` specifying the total sum of transaction value in the last 60 seconds
* `avg` – a `BigDecimal` specifying the average amount of transaction value in the last 60 seconds
* `max` – a `BigDecimal` specifying single highest transaction value in the last 60 seconds
* `min` – a `BigDecimal` specifying single lowest transaction value in the last 60 seconds
* `count` – a `long` specifying the total number of transactions that happened in the last 60 seconds
All `BigDecimal` values always contain exactly two decimal places and use `HALF_ROUND_UP` rounding. eg: 10.345 is 
returned as 10.35, 10.8 is returned as 10.80


`DELETE /transactions`

This endpoint causes all existing transactions to be deleted

The endpoint should accept an empty request body and return a 204 status code.

 
## Requirements
These are the additional requirements for the solution:

* You are free to choose any JVM language to complete the challenge in, but **your application has to run in Maven**.
* The API has to be threadsafe with concurrent requests.
* The `POST /transactions` and `GET /statistics` endpoints MUST execute in constant time and memory ie O(1). 
  Scheduled cleanup is not sufficient
* The solution has to work without a database (this also applies to in-memory databases).
* Unit tests are **mandatory**.
* `mvn clean install` and `mvn clean integration-test` must complete successfully.
* Please ensure that no changes are made to the `src/it` folder since they contain automated tests that will be used to 
  evaluate the solution.
* In addition to passing the tests, the solution must be at a quality level that you would be comfortable enough to put 
  in production.
  
## Commands
Use the following commands to work with this project
* Run - `mvn spring-boot:run`
* Install - `mvn clean install`
* Test - `mvn integration-test; cat target/customReports/result.txt`

## Solution
Due to the constant time and memory requirements, solutions using priority queues or something like that, where latest
transactions are added to the end and older ones removed from the beginning, don't work. We can't have an unbounded
queue to process when calculating the statistics.

Instead, we split the given duration for which statistics is required (60 seconds) into smaller windows (buckets), and
maintain local statistics for each of these buckets. The accuracy of the local statistics depends on the size of the 
window; smaller ones are more accurate, but it takes longer to aggregate them. Each buckets stores transactions for a
slice of time from the epoch; it also has a start timestamp. While adding a new transactions, if it falls in a bucket
whose timestamp is older than (transaction timestamp - duration), we reset that bucket and set its timestamp to the
beginning of the slice corresponding to the transaction being added.

For example, imagine the epoch is represented by 0, and we want to maintain last 30 seconds statistics. If each bucket
size is 1 second, we have 30 buckets. If we had unlimited number of buckets of size 1, a transaction with timestamp 
37 would fall into bucket (37 - 0) / 1 = 37; however, since we only have 30 buckets, we need to map the bucket 37 to 
an available bucket. We do that by a modulo operation that gives us (37 % 30) = 7. Bucket 7 will have a timestamp 
(0 + 1 * 37) = 37, meaning it will store transactions with timestamps in the range [37, 38).

With the above approach, calculating the overall statistics is easy; we simply iterate over the buckets, and aggregate
those buckets that have timestamp greater than or equal to (current timestamp - duration). Since there are a constant
number of buckets, we can do this in O(1) time and memory.

In order to support concurrent updates to the buckets, an [AtomicReferenceArray](https://cr.openjdk.java.net/~iris/se/11/latestSpec/api/java.base/java/util/concurrent/atomic/AtomicReferenceArray.html)
is used. Care if taken such that transactions and buckets are immutable. One interesting point to note is the memory
order mode used while aggregating the buckets; plain is used, which is the weakest, and has the semantics of reading 
as if the variable was declared non-volatile. Since the same thread is not doing read and write, plain and opaque modes
are equivalent in this case. Acquire and volatile modes provide stronger guarantees, but it doesn't seem like we need 
that.

## Technologies Used
* [Kotlin](https://kotlinlang.org/) - implementation language.
* [Micronaut](https://docs.micronaut.io/latest/guide/index.html) - base framework.