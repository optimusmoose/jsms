# Mass Spec - Trace Segmentation

The trace segmentation algorithm is derived from Rob Smith's
original Ruby code and lives in the `edu.umt.ms.traceSeg` namespace.
The "meat" of the code is in the `TraceSegmenter` Java class.

Trace segmentation depends on a `PointDatabaseConnection`; a reference
`HttpPointDatabaseConnection` has been provided with support for the
`MsHttpServer` API. Additionally, a `TraceParametersProvider` is used
to obtain the tuning parameters. This plugin may be removed in the
future if good enough defaults are determined. If desired, the two
interfaces can be reimplemented by consumers of this library to work
efficiently with their own data structures and other needs.

Trace segmentation works against a running MS HTTP server with the
reference implementation, and msViz has support for running
direct-access trace segmentation (as opposed to slow HTTP) in several
harnesses and as an option in the GUI (_Cluster_ tab, _Trace_ button).

## Metrics
In addition to trace segmentation itself, metrics have been implemented
in `edu.umt.ms.traceSeg.metrics`; Purity, NMI, SSE, and NTCD are
implemented at this time. These all run against CSV exports of
trace-segmented mass spec data.

## Usage
The project is packaged as a maven artifact. For code usage, see
`src/main/java/edu/umt/ms/traceSeg/TraceSegmentation.java` for an
example. This example uses an `HttpPointDatabaseConnection`
(connecting to a running MS HTTP server) and uses
`ConsoleTraceParametersProvider` to interactively request parameters
from the user at runtime.

## Algorithm
TraceSegmenter.java implements a probability-based single-pass assignment
of points to traces. The point with the highest intensity is assigned
first, followed by the next-highest point, until a point with a lower
intensity than the limit is reached. For each point, all "nearby" existing
traces, that is, traces close enough that the point could be included
in them, are checked. The trace with the highest probability score is
selected, as long as it is within the probability threshold. If no trace
scores the point within the probability threshold, a new trace is created.
The point is assigned to the trace and the trace's statistics are updated
for use when inspecting future points.

The probability score is a function of distance in m/z, distance in RT,
and intensity. For m/z and RT, closer points are higher-scoring. For
intensity, less-intense points are more likely to be associated with any
trace and more-intense points are less likely to be associated with any
trace. Additionally, no point can be added to an existing trace unless
its intensity is strictly lower than the intensities of all other points
currently in the trace.
