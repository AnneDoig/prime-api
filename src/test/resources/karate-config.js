function fn() {
    var port = java.lang.System.getProperty('test.server.port', '8080');
    return {
        baseUrl: 'http://localhost:' + port
    };
}