# Senda — Frontend

React + Vite + Tailwind CSS (JavaScript, no UI component library).

## Scripts

- `npm run dev` — dev server at http://localhost:5173
- `npm test` — run tests (Vitest + Testing Library)
- `npm run build` — production build
- `npm run lint` — ESLint

## Configuration

- `VITE_API_URL` — API base URL (default `http://localhost:8080/api`, see `.env.development`).

## Structure

- `src/api/http.js` — central fetch wrapper (JWT, JSON, `ApiError`, 401 handling)
- `src/auth/` — `AuthContext`, `useAuth`, `ProtectedRoute`
- `src/components/` — app shell (`Layout`), auth shell, form primitives
- `src/pages/` — Login, Register, Dashboard, Transactions, Categories
- `src/test/setup.js` — Vitest setup (jest-dom, jsdom localStorage fix)
