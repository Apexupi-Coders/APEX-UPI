VM–Dashboard Integration
Overview
The VM–Dashboard Integration module provides a centralized monitoring layer for the APEX-UPI platform. It connects the React Operations Dashboard with the live backend control API and presents consolidated operational metrics through a single aggregation endpoint.
Instead of allowing the frontend to communicate directly with multiple backend services, the Operations Dashboard API acts as an aggregation layer that retrieves live system information, transforms it into a unified format, and exposes a simplified REST API for visualization.

Architecture




                  React Operations Dashboard
                     (Port 5173)
                           │
                           │ GET /api/v1/ops/overview
                           ▼
          Operations Dashboard API (Aggregation Layer)
                     (Port 8081)
                           │
                           │ REST Call
                           ▼
        Live Control Status API (Hosted VM)
                           │
                           ▼
      PostgreSQL | Redis | Kafka | NPCI | CBS


Components
  
Dashboard UI
1.Built using React and Vite
2.Displays live operational metrics
3.Auto-refreshes dashboard data
4.Consumes only a single aggregation endpoint

Operations Dashboard API

Responsible for:
1.Fetching live VM status
2.Aggregating operational information
3.Transforming backend responses
4.Handling unavailable services gracefully
5.Providing a unified REST endpoint

Live Control API

The aggregation service consumes the live control endpoint:
GET /api/v1/control/status
This endpoint returns:
  Infrastructure information
  Transaction statistics
  Failure mode flags
  Service metadata

Aggregation Workflow
  
  React Dashboard
      │
      ▼
GET /api/v1/ops/overview
      │
      ▼
HealthAggregationService
      │
      ▼
GET Live Control API
      │
      ▼
Transform Response
      │
      ▼
Return Dashboard DTO
      │
      ▼
Render Dashboard

Data Transformation
The live backend response is mapped into dashboard-friendly objects.
Live Backend Response
Infrastructure
Transaction Counts
Failure Toggles
Service Sizes
↓

Aggregated Dashboard Response
timestamp

overallStatus

healthyCount

unhealthyCount

degradedCount

totalCount

services[]
Each dashboard card is generated from the transformed response.

  
Dashboard Features
Live infrastructure monitoring
Transaction statistics
System health overview
Failure mode indicators
Auto-refresh support
Read-only monitoring interface
Backend abstraction through aggregation layer
  
REST Endpoints
  
Dashboard Aggregation API
GET /api/v1/ops/overview
Returns
Overall system status
Service list
Health summary
Infrastructure details
Transaction metrics
  
Spring Boot Health
GET /actuator/health
Used for application health verification.
  
Aggregation Logic
  
The HealthAggregationService performs the following steps:
Calls the live VM control API.
Retrieves operational metrics.
Maps raw JSON into Java DTOs.
Computes overall dashboard health.
Converts backend data into dashboard service cards.
Returns a unified response to the frontend.
  
Benefits
  
Single API for frontend consumption
Decouples UI from backend services
Simplifies future service integration
Centralized monitoring logic
Consistent response format
Improved maintainability
Graceful error handling
  
Technologies Used
  
Java 17
Spring Boot
Spring Web
Spring Actuator
React
TypeScript
Vite
REST APIs
Maven
  
Future Enhancements
  
Real-time WebSocket updates
Historical health analytics
Alert notifications
Service latency trends
Authentication and authorization
Multi-VM monitoring
Prometheus/Grafana integration
  
Outcome
  
Successfully implemented a centralized VM–Dashboard Integration solution where the Operations Dashboard API acts as an aggregation layer between the React dashboard and the live backend control API, enabling real-time monitoring, data transformation, health aggregation, and simplified frontend consumption through a single REST endpoint.
