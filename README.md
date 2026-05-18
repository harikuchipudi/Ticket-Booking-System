# IPL Ticket Booking System (Angular + Spring Boot)

This workspace contains a Spring Boot backend scaffold and instructions to create an Angular frontend.

Backend (Spring Boot):
- Location: `backend/`
- Run: `mvn -f backend/ spring-boot:run`

Frontend (Angular):
We recommend generating the Angular app using the Angular CLI and then copying the sample service/component provided under `frontend/samples`.

Suggested commands:

```bash
# Generate an Angular app (run in workspace root)
npx @angular/cli@latest new frontend --routing=false --style=css --skip-git

# Start backend (from workspace root)
cd backend && mvn spring-boot:run

# Start frontend
cd frontend && npm install && ng serve
```

After generating the Angular project, copy the files in `frontend/samples` into `frontend/src/app/` and register the component in `AppModule`.

See `frontend/samples` for example Angular service and component code to call the backend API.
