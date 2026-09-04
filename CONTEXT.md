# Foldlytics Usage Measurement

Foldlytics describes display and app usage that can be supported by locally recorded Android
events, while keeping unclassified intervals explicit.

## Language

**Classified display time**:
App-visible, interactive, unlocked time for which the outer or inner display could be determined.
_Avoid_: Total observed time

**Display-undetermined time**:
App-visible, interactive, unlocked time for which neither display could be determined. It remains
outside outer/inner shares rather than being assigned by inference.
_Avoid_: Missing time, estimated display time

**App display share**:
The outer or inner portion of an app's classified display time within the selected period.
_Avoid_: App preference, app value

**Display-majority app**:
An app with more than half of its classified display time on one display. An even split has no
display majority.
_Avoid_: Preferred-display app, foldable-value app
