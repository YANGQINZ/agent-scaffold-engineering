/**
 * 条件边
 * 灰色贝塞尔曲线，中间显示条件标签
 */
import { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type EdgeProps,
} from '@xyflow/react';

function ConditionEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  markerEnd,
}: EdgeProps) {
  const condition = (data as Record<string, unknown> | undefined)?.condition as string | undefined;

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{ stroke: '#94a3b8', strokeWidth: 1.5 }}
      />
      {condition && (
        <EdgeLabelRenderer>
          <div
            className="pointer-events-auto absolute rounded-md bg-white px-2 py-0.5 text-xs text-gray-600 shadow-sm ring-1 ring-gray-200"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            }}
          >
            {condition}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export default memo(ConditionEdge);
