# LIBRARY-HTTPServer
Simple HTTP-Server implementation

This is a simple implementation of the HTTP-protocol.
A HttpServer can be constructed from an ServerSocket and handlers for get/head, put, post and delete can be set.
Originally developed for an small embedded project.

Since v1.2 it also includes an Stream-Based WebSocket implementation.
It also comes with an helper method for upgrading incomming HTTP requests to WebSocket connections.
