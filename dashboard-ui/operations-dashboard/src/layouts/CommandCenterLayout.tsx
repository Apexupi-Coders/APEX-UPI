import React from 'react';
import { Outlet } from './Router';
import { TopBar } from '../components/TopBar';
import { SideNav } from '../components/SideNav';
import { ErrorBoundary } from '../components/error/ErrorBoundary';

/**
 * Production-style Operations Command Center shell.
 *
 * Read-only UX: no mutations, no side effects.
 */
export default function CommandCenterLayout() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <TopBar />

      <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-4 p-4">
        <aside className="col-span-2 hidden md:block">
          <SideNav />
        </aside>

        <section className="col-span-12 md:col-span-10">
          <ErrorBoundary fallback={<div className="text-sm">Something went wrong.</div>}>
            <Outlet />
          </ErrorBoundary>
        </section>
      </div>
    </div>
  );
}

