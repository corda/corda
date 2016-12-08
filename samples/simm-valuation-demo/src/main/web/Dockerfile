FROM fredead/angular-cli
MAINTAINER  Simon Loader simon.loader@r3cev.com

COPY . /app
WORKDIR "/app"
run npm install
run npm run postinstall
run ng build --prod

expose 4200

ENTRYPOINT ["ng","serve", "--prod"]
