import React from 'react';
import ExecutiveOverviewPage from './ExecutiveOverviewPage';
import LiveTransactionMonitorPage from './LiveTransactionMonitorPage';
import RealTimeEventStreamPage from './RealTimeEventStreamPage';
import TransactionJourneyPage from './TransactionJourneyPage';
import KafkaMonitoringPage from './KafkaMonitoringPage';
import ReconciliationPage from './ReconciliationPage';
import LedgerPage from './LedgerPage';
import AuditPage from './AuditPage';
import ErrorIntelligencePage from './ErrorIntelligencePage';
import ServiceHealthPage from './ServiceHealthPage';
import ArchitectureFlowPage from './ArchitectureFlowPage';
import DemoModePage from './DemoModePage';
import OverviewPage from './OverviewPage';
import ArchitecturePage from './ArchitecturePage';
import type { RouteId } from '../layouts/Router';


export function getPage(route: RouteId) {
  switch (route) {
    case 'executive':
      return OverviewPage;
    case 'live':
      return LiveTransactionMonitorPage;
    case 'events':
      return RealTimeEventStreamPage;
    case 'journey':
      return TransactionJourneyPage;
    case 'kafka':
      return KafkaMonitoringPage;
    case 'reconciliation':
      return ReconciliationPage;
    case 'ledger':
      return LedgerPage;
    case 'audit':
      return AuditPage;
    case 'errors':
      return ErrorIntelligencePage;
    case 'health':
      return ServiceHealthPage;
    case 'architecture':
      return ArchitecturePage;

    case 'demo':
      return DemoModePage;
    default:
      return OverviewPage;
  }
}

export {
  ExecutiveOverviewPage,
  OverviewPage,
  LiveTransactionMonitorPage,
  RealTimeEventStreamPage,
  TransactionJourneyPage,
  KafkaMonitoringPage,
  ReconciliationPage,
  LedgerPage,
  AuditPage,
  ErrorIntelligencePage,
  ServiceHealthPage,
  ArchitectureFlowPage,
  DemoModePage,
};


