const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const rateLimit = require('express-rate-limit');
const jwt = require('jsonwebtoken');

const app = express();
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'supersecretkey_microsmart';

// 1. Rate Limiting (100 req/min)
const limiter = rateLimit({
    windowMs: 60 * 1000, // 1 minuto
    max: 100, // Limita a 100 peticiones por IP cada minuto
    message: "Demasiadas peticiones desde esta IP, por favor intente de nuevo después de un minuto."
});
app.use(limiter);

// 2. Middleware de Validación Global de JWT
const authMiddleware = (req, res, next) => {
    // Excluir rutas de login/registro si las hubiera, por ahora protegemos todo el API
    const authHeader = req.headers['authorization'];
    if (!authHeader) return res.status(401).send('Acceso denegado. No se proporcionó token.');

    const token = authHeader.split(' ')[1];
    if (!token) return res.status(401).send('Acceso denegado. Formato de token inválido.');

    try {
        const verified = jwt.verify(token, JWT_SECRET);
        req.user = verified;
        next();
    } catch (err) {
        res.status(400).send('Token inválido o expirado.');
    }
};

// 3. Configuración de API Gateway - Rutas de Proxy
app.use('/api/reservas', authMiddleware, createProxyMiddleware({ 
    target: process.env.MS_RESERVAS_URL || 'http://localhost:3001', 
    changeOrigin: true 
}));

app.use('/api/solicitudes', authMiddleware, createProxyMiddleware({ 
    target: process.env.MS_SOLICITUDES_URL || 'http://localhost:3002', 
    changeOrigin: true 
}));

app.use('/api/personalizacion', authMiddleware, createProxyMiddleware({ 
    target: process.env.MS_PERSONALIZACION_URL || 'http://localhost:3003', 
    changeOrigin: true 
}));

app.listen(PORT, () => {
    console.log(`API Gateway escuchando en el puerto ${PORT}`);
});
