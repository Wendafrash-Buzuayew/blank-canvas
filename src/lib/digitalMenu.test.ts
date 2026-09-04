// src/lib/digitalMenu.test.ts
import { buildDigitalMenuApiPath } from './digitalMenu';

function ok(name: string, condition: boolean) {
  console.log(`  ${condition ? 'ok' : 'FAIL'}  ${name}`);
  if (!condition) process.exitCode = 1;
}

ok(
  'primary-branch resolution path has no branch segment',
  buildDigitalMenuApiPath('sunrise-coffee') === '/api/v1/public/digital-menu/sunrise-coffee',
);

ok(
  'branch resolution path includes the branch slug',
  buildDigitalMenuApiPath('sunrise-coffee', 'main') === '/api/v1/public/digital-menu/sunrise-coffee/main',
);

ok(
  'slugs are URI-encoded',
  buildDigitalMenuApiPath("joe's-diner", 'main') === "/api/v1/public/digital-menu/joe's-diner/main"
    ? false // a literal apostrophe must NOT pass through unencoded
    : buildDigitalMenuApiPath("joe's-diner", 'main') === '/api/v1/public/digital-menu/joe%27s-diner/main',
);

console.log(process.exitCode ? 'FAILED' : 'all digitalMenu tests passed');
