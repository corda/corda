# Vega

This project was generated with [angular-cli](https://github.com/angular/angular-cli) version 1.0.0-beta.10.

## How to build for production
`npm install -g angular-cli@1.0.0-beta.10`
`npm install`
in src/app/index.html replace `<base href="/">` with `<base href="/web/vega/">` (TODO: fix this)
in src/app/system-config.ts uncomment `//baseURL: "/web/vega",` (TODO: fix this)
`ng build -prod`
the dist build will be generated in `dist` folder

## Troubleshooting
On a mac, when doing ng serve you may get an error like:
`The Broccoli Plugin: [BroccoliMergeTrees] failed with:
Error: Merge error: file .DS_Store exists in /Users/Paolo/Dropbox/Code/R3/vega-main/vega/src/main/web/tmp/broccoli_merge_trees-input_base_path-0r7SyUJT.tmp/0 and /Users/Paolo/Dropbox/Code/R3/vega-main/vega/src/main/web/tmp/broccoli_merge_trees-input_base_path-0r7SyUJT.tmp/1
Pass option { overwrite: true } to mergeTrees in order to have the latter file win.
at BroccoliMergeTrees._mergeRelativePath (/Users/Paolo/Dropbox/Code/R3/vega-main/vega/src/main/web/node_modules/angular-cli/node_modules/broccoli-merge-trees/index.js:266:17)
at BroccoliMergeTrees.build (/Users/Paolo/Dropbox/Code/R3/vega-main/vega/src/main/web/node_modules/angular-cli/node_modules/broccoli-merge-trees/index.js:70:24)
...`

[see here](http://stackoverflow.com/questions/38549080/broccoli-mergeerror-for-ds-store-file-in-angular-cli)

## Development server
Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive/pipe/service/route/class`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory. Use the `-prod` flag for a production build.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via [Protractor](http://www.protractortest.org/). 
Before running the tests make sure you are serving the app via `ng serve`.

## Deploying to Github Pages

Run `ng github-pages:deploy` to deploy to Github Pages.

## Further help

To get more help on the `angular-cli` use `ng --help` or go check out the [Angular-CLI README](https://github.com/angular/angular-cli/blob/master/README.md).
