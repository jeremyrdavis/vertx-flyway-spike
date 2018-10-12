create table if not exists USER ("id" INT IDENTITY PRIMARY KEY ,
  "username" VARCHAR(255) NOT NULL ,
  "email" VARCHAR(255) NOT NULL ,
  "bio" VARCHAR(255) ,
  "image" VARCHAR(255) ,
  "password" VARCHAR(255) NOT NULL ,
  "password_salt" VARCHAR(255));
