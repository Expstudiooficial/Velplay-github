import React, { useState, useRef, useEffect, useMemo } from 'react';

interface VirtualListProps<T> {
  items: T[];
  itemHeight: number;
  renderItem: (item: T, index: number) => React.ReactNode;
  containerHeight: string | number; // e.g. '100%', 500
  Footer?: React.ReactNode;
  footerHeight?: number;
}

export function VirtualList<T>({ items, itemHeight, renderItem, containerHeight, Footer, footerHeight = 0 }: VirtualListProps<T>) {
  const [scrollTop, setScrollTop] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  
  const [windowHeight, setWindowHeight] = useState(800); // Default fallback

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const observer = new ResizeObserver((entries) => {
      for (let entry of entries) {
        setWindowHeight(entry.contentRect.height);
      }
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, [containerHeight]);

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    setScrollTop(e.currentTarget.scrollTop);
  };

  const totalHeight = items.length * itemHeight;
  
  // Calculate start and end indices
  const overscan = 5; // render extra items to prevent flashing
  const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
  const endIndex = Math.min(
    items.length - 1,
    Math.ceil((scrollTop + windowHeight) / itemHeight) + overscan
  );

  const visibleItems = useMemo(() => {
    return items.slice(startIndex, endIndex + 1).map((item, index) => {
      const realIndex = startIndex + index;
      return (
        <div
          key={realIndex}
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: `${itemHeight}px`,
            transform: `translateY(${realIndex * itemHeight}px)`,
          }}
        >
          {renderItem(item, realIndex)}
        </div>
      );
    });
  }, [items, startIndex, endIndex, itemHeight, renderItem]);

  return (
    <div
      ref={containerRef}
      style={{ height: containerHeight, overflowY: 'auto', position: 'relative' }}
      onScroll={handleScroll}
      className="custom-scrollbar" // Custom scrollbar via CSS
    >
      <div style={{ height: `${totalHeight + (Footer ? footerHeight : 0)}px`, position: 'relative', width: '100%' }}>
        {visibleItems}
        {Footer && (
          <div style={{ position: 'absolute', top: totalHeight, left: 0, width: '100%', height: footerHeight }}>
            {Footer}
          </div>
        )}
      </div>
    </div>
  );
}
