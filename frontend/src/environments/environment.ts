const isLocal = window.location.hostname === 'localhost';

export const environment = {
  production: !isLocal,
  apiUrl: isLocal ? 'http://localhost:8082' : 'https://ticket-booking-backend-v0hz.onrender.com'
};
