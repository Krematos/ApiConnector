import http from 'k6/http';
import { check, sleep } from 'k6';

// Konfigurace testu:
// - vus: Virtual Users (počet souběžných uživatelů)
// - duration: Jak dlouho test poběží
export const options = {
  stages: [
    { duration: '10s', target: 5 },  // Ramp-up: Během 10s nastoupej na 5 uživatelů
    { duration: '30s', target: 20 }, // Load: Drž 20 uživatelů po dobu 30s
    { duration: '10s', target: 0 },  // Ramp-down: Vypni to
  ],
};

export default function () {
  const url = 'http://localhost:8080/api/middleware/v1/transaction';

  // Náhodná data, aby grafy vypadaly zajímavě
  const randomAmount = Math.floor(Math.random() * 1000) + 1;
  const currency = Math.random() > 0.5 ? 'CZK' : 'EUR';

  const payload = JSON.stringify({
    internalOrderId: `TEST-${Date.now()}-${__VU}-${__ITER}`, // Unikátní ID
    amount: randomAmount,
    currencyCode: currency,
    serviceType: 'PAYMENT',
    requestedAt: new Date().toISOString() // ISO timestamp required by InternalRequest
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-KEY': 'moje-tajne-heslo-12345',
    },
  };

  const res = http.post(url, payload, params);

  if (res.status !== 200) {
    console.log(`CHYBA: Status ${res.status}`);
    console.log(`Response: ${res.body}`);
  }

  // Kontrola, zda server odpověděl 200 OK
  check(res, {
    'status was 200': (r) => r.status === 200,
  });

  // Pauza mezi requesty (simulace reálného uživatele),
  sleep(1);
}