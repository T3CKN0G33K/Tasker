import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';

export class ApiError extends Error {
  constructor(public status: number, public code: string) { super(code); }
}
const googleKeys = createRemoteJWKSet(new URL(
  'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com',
));

export async function verifyFirebaseToken(token: string, project: string, keys: JWTVerifyGetKey = googleKeys) {
  try {
    const { payload } = await jwtVerify(token, keys, {
      algorithms: ['RS256'], issuer: `https://securetoken.google.com/${project}`, audience: project,
      requiredClaims: ['sub', 'exp', 'iat', 'auth_time'],
    });
    const now = Math.floor(Date.now() / 1000);
    if (!payload.sub || payload.sub.length > 128 || typeof payload.iat !== 'number' || payload.iat > now ||
        typeof payload.auth_time !== 'number' || payload.auth_time > now) throw new Error();
    return payload.sub;
  } catch { throw new ApiError(401, 'unauthenticated'); }
}

function base64(bytes: Uint8Array) { return btoa(String.fromCharCode(...bytes)); }
function unbase64(text: string) { return Uint8Array.from(atob(text), c => c.charCodeAt(0)); }
async function key(secret: string) {
  let bytes: Uint8Array<ArrayBuffer>;
  try { bytes = unbase64(secret); } catch { throw new ApiError(503, 'configuration_required'); }
  if (bytes.length !== 32) throw new ApiError(503, 'configuration_required');
  return crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt']);
}
export async function encryptToken(token: string, secret: string, owner: string) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({name: 'AES-GCM', iv,
    additionalData: new TextEncoder().encode(owner)}, await key(secret), new TextEncoder().encode(token));
  return `v1.${base64(iv)}.${base64(new Uint8Array(encrypted))}`;
}
export async function decryptToken(value: string, secret: string, owner: string) {
  const [version, iv, data] = value.split('.');
  if (version !== 'v1' || !iv || !data) throw new ApiError(503, 'configuration_required');
  const decrypted = await crypto.subtle.decrypt({name: 'AES-GCM', iv: unbase64(iv),
    additionalData: new TextEncoder().encode(owner)}, await key(secret), unbase64(data));
  return new TextDecoder().decode(decrypted);
}
