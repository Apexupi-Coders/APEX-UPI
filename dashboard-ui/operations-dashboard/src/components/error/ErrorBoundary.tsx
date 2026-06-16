import React from 'react';

type Props = {
  children: React.ReactNode;
  fallback?: React.ReactNode;
};

type State = {
  hasError: boolean;
  errorMessage?: string;
};

export class ErrorBoundary extends React.Component<Props, State> {
  declare state: State;
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }


  static getDerivedStateFromError(error: unknown) {
    return {
      hasError: true,
      errorMessage: error instanceof Error ? error.message : String(error),
    };
  }

  componentDidCatch() {
    // Intentionally no side-effects in observability layer.
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="rounded-xl border border-rose-900 bg-rose-950/40 p-4">
          <div className="text-sm font-semibold text-rose-200">Module failed</div>
          <div className="mt-1 text-xs text-rose-200/80">
            {this.state.errorMessage ?? 'Unknown error'}
          </div>
          {this.props.fallback}
        </div>
      );
    }

    return this.props.children;
  }
}

