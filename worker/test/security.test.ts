import { describe, expect, it } from 'vitest';
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from 'jose';
import { decryptToken, encryptToken, verifyFirebaseToken } from '../src/security';
const secret = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';
describe('token protection', () => {
  it('uses randomized authenticated encryption bound to the owner and Item', async () => {
    const encrypted = await encryptToken('test-only-value', secret, 'alice:item');
    expect(encrypted).not.toContain('test-only-value');
    expect(await encryptToken('test-only-value', secret, 'alice:item')).not.toBe(encrypted);
    expect(await decryptToken(encrypted, secret, 'alice:item')).toBe('test-only-value');
    await expect(decryptToken(encrypted, secret, 'bob:item')).rejects.toThrow();
    await expect(decryptToken(encrypted.slice(0,-3)+'AAA', secret, 'alice:item')).rejects.toThrow();
  });
  it('rejects invalid encryption key lengths', async () => {
    await expect(encryptToken('value', 'AAAA', 'alice:item')).rejects.toThrow();
  });
});
describe('Firebase verification', () => {
  it('verifies signatures, project, issuer, expiry, subject and auth_time', async () => {
    const {privateKey, publicKey} = await generateKeyPair('RS256');
    const jwk = await exportJWK(publicKey); jwk.kid = 'test'; jwk.alg = 'RS256';
    const keys = createLocalJWKSet({keys: [jwk]});
    const now = Math.floor(Date.now()/1000);
    async function sign(overrides = {}) {
      return new SignJWT({sub:'alice', aud:'project', iss:'https://securetoken.google.com/project',
        exp:now+300, iat:now, auth_time:now, ...overrides})
        .setProtectedHeader({alg:'RS256',kid:'test'}).sign(privateKey);
    }
    expect(await verifyFirebaseToken(await sign(), 'project', keys)).toBe('alice');
    for (const overrides of [{aud:'other'}, {iss:'other'}, {exp:now-10}, {sub:''}, {auth_time:now+60}, {iat:now+60}]) {
      await expect(verifyFirebaseToken(await sign(overrides), 'project', keys)).rejects.toThrow();
    }
    await expect(verifyFirebaseToken('not-a-token', 'project', keys)).rejects.toThrow();
  });
});
