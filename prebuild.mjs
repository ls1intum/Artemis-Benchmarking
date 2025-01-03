import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
// =====================
// Environment variables
// =====================

/*
 * Needed for client compilations with docker compose, where the 'APP_VERSION' property isn't injected by gradle.
 *
 * Returns the inferred APP_VERSION from 'build.gradle', or 'DEV' if this couldn't be retrieved
 */
function inferVersion() {
  let version = 'DEV';
  try {
    let data = fs.readFileSync('build.gradle', 'UTF-8');

    version = data.match(/\nversion\s=\s"(.*)"/);

    version = version[1] ?? 'DEV';
  } catch (error) {
    console.log("Error while retrieving 'APP_VERSION' property: " + error);
  }

  return version;
}

// --develop flag is used to enable debug mode
const args = process.argv.slice(2);
const developFlag = args.includes('--develop');
const environmentConfig = `// Don't change this file manually, it will be overwritten by the build process!
export const environment = {
  VERSION: '${process.env.APP_VERSION || inferVersion()}',
  DEBUG_INFO_ENABLED: ${developFlag},
};
`;
fs.writeFileSync(path.resolve(__dirname, 'src', 'main', 'webapp', 'app', 'environments', 'environment.override.ts'), environmentConfig);
