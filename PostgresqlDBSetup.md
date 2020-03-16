## Commands that maybe useful to set up the multiple databases

initdb path -U cs223 -W

initdb path -U cs223 -W

initdb path -U cs223 -W

sudo chmod 0700 path

sudo chmod 0700 path

sudo chmod 0700 path

pg_ctl -D path -o "-p 5440" -l path/log1 start

pg_ctl -D path -o "-p 5441" -l path/log2 start

pg_ctl -D path -o "-p 5442" -l path/log3 start

pg_ctl -D path -o "-p 5440" -l path/log1 stop

pg_ctl -D path -o "-p 5441" -l path/log2 stop

pg_ctl -D path -o "-p 5442" -l path/log3 stop

psql -U cs223 -p 5440

psql -U cs223 -p 5441

psql -U cs223 -p 5442
