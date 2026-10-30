# bookstorebackend

Bookstore REST API: Spring Boot 3, MySQL, JWT authentication and password reset by email.
The frontend is [bookstorefrontend](https://github.com/kolehmainen-works/bookstorefrontend)
(live: https://kolehmainen-works.github.io/bookstorefrontend.github.io/).

## API

Send the token from the login as `Authorization: Bearer <token>`.

| Endpoint | Access |
|---|---|
| `POST /api/auth/signup` `{username, email, password, passwordCheck}` → same as login | everyone |
| `POST /api/auth/login` `{username, password}` → `{token, expiresAt, username, roles}` | everyone |
| `POST /api/auth/forgot-password` `{email}` | everyone |
| `POST /api/auth/reset-password` `{token, password, passwordCheck}` | everyone |
| `GET /api/books`, `GET /api/books/{id}` | signed in: own books; admin: all |
| `POST /api/books` | signed in; the book belongs to the signed-in user |
| `PUT /api/books/{id}`, `DELETE /api/books/{id}` | the book's owner, or an admin |
| `GET /api/categories` | signed in |
| `DELETE /api/users/me` | signed in; deletes the account and its books |

Each book belongs to the user who added it (`ownerUsername` in the JSON). Other users' books
answer 404 as if they did not exist. Signup creates `ROLE_USER` accounts.

An empty database gets demo data: users `user`/`user` and `admin`/`admin`, and twelve books
owned by `user`. Books without an owner are given to `user` at startup.

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
