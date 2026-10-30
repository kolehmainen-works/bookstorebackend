# bookstorebackend

Bookstore REST API: Spring Boot 3, MySQL, JWT authentication and password reset by email.
The frontend is [bookstorefrontend](https://github.com/kolehmainen-works/bookstorefrontend)
(live: https://kolehmainen-works.github.io/bookstorefrontend.github.io/).

## API

Send the token from the login as `Authorization: Bearer <token>`.

| Endpoint | Access |
|---|---|
| `POST /api/auth/login` `{username, password}` → `{token, expiresAt, username, roles}` | everyone |
| `POST /api/auth/forgot-password` `{email}` | everyone |
| `POST /api/auth/reset-password` `{token, password, passwordCheck}` | everyone |
| `GET /api/books`, `GET /api/books/{id}`, `GET /api/categories` | signed in |
| `POST /api/books`, `PUT /api/books/{id}` | signed in |
| `DELETE /api/books/{id}` | `ROLE_ADMIN` |

An empty database gets demo data: users `user`/`user` and `admin`/`admin`, and twelve books.

## Configuration

Set these environment variables on the server:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | at least 32 characters; without it tokens stop working after a restart |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP login (mail.kapsi.fi) for the password reset emails |

Database, HTTPS keystore, port (8090) and the frontend address are in
`src/main/resources/application.properties`.

## Build and test

```bash
./mvnw clean package   # tests use an in-memory H2 database
```

## Deploy to softala.haaga-helia.fi

```bash
./commit-and-deploy.sh "commit message"
```

Commits and pushes, builds, copies the jar to the server and restarts `iinakbookapp.service`.
The server password is read from `DEPLOY_PASSWORD`, a git-ignored `.deploy-password` file,
or asked for.
