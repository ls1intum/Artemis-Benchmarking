module.exports = {
  'src/{main/webapp,test}/**/*.{json,js,ts,css,scss,html}': ['prettier --write'],
  'src/{main/webapp,test}/**/*.{ts,html}': ['eslint --fix'],
  // Prettier, not Gradle, is what CI enforces for Java (`pnpm run prettier:check` covers `**/*.java`), and the
  // spotless block in build.gradle has no formatter steps configured, so `linting.sh` formatted nothing. Java
  // changes therefore reached CI unformatted and failed there. Run the formatter CI actually checks.
  'src/{main,test}/java/**/*.java': ['prettier --write'],
};
