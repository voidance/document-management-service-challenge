# Setting up the environment

This project can run as a standalone Spring Boot app, or as part of the [docker-compose](docker/docker-compose.yml).

To do life easier for the reviewer the `.env` files have been filled with correct parameters for
running in `local`. IRL we wouldn't push these changes to the repo. `.env` files would be added to gitignore.

## Running document-management-service-challenge in standalone mode

From the project root directory, go to the docker directory:

```
cd docker
```

Run docker compose up:

```
docker compose up --build
```

This will create all the needed infra and set up the Minio bucket.

Note: You can add the `-d` option to run docker compose detached.

Effect the required configuration from the project root directory:

```
set -a
source .env
set +a
```

Start the `document-management-service-challenge` service:

```
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx80m -Xms80m"

```

## Running document-management-service-challenge as part of docker-compose

Create the document-management-service challenge image by running:

```
./mvnw spring-boot:build-image -DskipTests
```

Once the image is built, do:

```
docker compose --profile with-document-management-service up --build
```

This will start the required services (Minio, PostgreSqL) and the document-management-service-challenge.

# Running tests:

Simply run from the root directory:

```
./mvnw clean test  
```

There is a poor mans performance test in `src/test/performance/parallelfiles.sh`

Before running it you need to generate pdf files of 500mb.

To do so run `dd if=/dev/zero of=file-0.pdf bs=1M count=500` and copy the files to be named from `file-0.pdf` to `file-10.pdf`

Then you can run the performance test by doing `./parallelfiles.sh`

Would have been better to do a JMeter test but no time for now.

# Considerations and approach taken to solve the exercise

## Memory requirements

Though the exercise mentioned not using more than 50 mb for the document-management service. After testing various scenarios,
the least amount of memory I could test the service to run 10 requests in parallel uploading a file of 500 mb each was with 80mb. If I use
less, then the service will through OutOfHeap memory error.

```
-Dspring-boot.run.jvmArguments="-Xmx80m -Xms80m"
```

I also noticed that I could consume less memory if somehow uploading the files to Minio could be done streaming one by one, as opposed to stream
the 10 files to Minio at the same time. Even do for a real world app it wouldn't make much sense, for the sake of doing the exercise
with as least amount of memory possible a semaphore was introduced in the MinioService upload. This can be configured with MinioConfig to increase or
decrease upload parallelism.

## Other improvements

### Flyway migration

Instead of storing the db schema in the `init-scripts` I introduced Flyway. This way we have the schema changes versioned.
See the [db.migration](src/main/resources/db/migration/V1__initial_schema.sql) folder to check the SQL schema.

### Docker file for document-service-challenge

There is no need to manually create the docker file and we can use `spring-boot:build-image` to build a docker image for us.

### Configuration

All the configuration parameters are externalized and can be set per environment/CI. In `env.template` file there is an example.
We can use the template to create per environment configurations.

To effect a configuration file run:

```
set -a
source .env
set +a
```

And then for instance:

```
./mvnw spring-boot:run
```

Or use dotenv if installed

```
dotenv -e .env ./mvnw spring-boot:run 
```

### Docker compose has been split with profiles

Splitting docker compose per profiles makes life easier to launch the application in local environment.

To launch all the required services use:

```
docker compose --profile with-document-management-service up
```

To just launch the dependencies for Document Management Service use:

```
docker compose up --build
```

## General solution comments

* Since there are no requirements for authentication & authorization, every user can see what every other user uploaded.
* The ids for `document` are serial numbers, which leaks information about documents (i.e., how many documents are uploaded). It is assumed to be an internal service only. Otherwise it could be improved by setting the id to be a UUID or one of the shorter versions.

## File upload

When uploading a file storing the file in MinIO is done first. If this fails only the file is in minio
and nothing gets stored in the db. An error is returned to the user and a search won't be able to find the doc.
Some sort of cleanup job could be implemented if this is an issue as a first step.

### Postman requests

I have created a Postman Workspace with requests examples at

https://web.postman.co/workspace/8f4a0a29-d262-4439-b780-3a324898d1d6

Send me and invite and I can share it
