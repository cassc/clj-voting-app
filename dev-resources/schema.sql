--  TODO foreign key to be TESTED

drop table vote;
drop table options;
drop table poll;
drop table tuser;



create table tuser(
`id` integer primary key,
user_id integer unique not null,
screen_name text not null,
oauth_token text null,
oauth_token_secret text null
);


create table poll(
`id` integer primary key,
creator_id integer not null,
title text not null,
foreign key (creator_id) references tuser(user_id)
);

create table options(
`id` integer primary key,
poll_id integer not null,
title text not null,
foreign key (poll_id) references poll(id),
unique (poll_id, title)
);

create table vote(
`id` integer primary key,
poll_id integer not null,
user_id integer not null,
opt_id integer not null,
foreign key (user_id) references tuser(user_id),
foreign key (poll_id) references poll(id),
foreign key (opt_id) references options(id),
unique (poll_id, user_id)
);


