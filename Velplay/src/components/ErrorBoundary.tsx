// @ts-nocheck
import React, { Component, ErrorInfo, ReactNode } from "react";

interface Props {
  children?: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends React.Component<Props, State> {
  public state: State = {
    hasError: false
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught error:", error, errorInfo);
  }

  public render() {
    const props = this.props as Props;
    if (this.state.hasError) {
      return props.fallback || (
        <div className="flex flex-col items-center justify-center p-8 h-full bg-[#0a0a0f] text-white">
          <h2 className="text-xl font-bold mb-4 text-red-500">Something went wrong.</h2>
          <pre className="text-sm bg-black/50 p-4 rounded text-left overflow-auto max-w-2xl max-h-96">
            {this.state.error?.message}
          </pre>
          <button 
            onClick={() => this.setState({ hasError: false, error: undefined })}
            className="mt-6 px-4 py-2 bg-indigo-500 hover:bg-indigo-600 rounded-full font-semibold transition-colors"
          >
            Try Again
          </button>
        </div>
      );
    }

    return props.children;
  }
}
