# Why Foldlytics exists

[日本語](CONCEPT-ja.md)

## The question behind the app

If I rarely unfold a foldable phone, I am left with a phone that is heavier and
more expensive than a conventional one.

That does not make foldables pointless. The inner display can make reading,
video, maps, multitasking, or work better for one person, while another person
may leave it closed most of the time. Specifications and reviews cannot answer
the personal question that matters here:

> Does a foldable improve the way I actually use my phone?

I built Foldlytics because memory is not good enough to answer that question.
It replaces "I don't think I open it very often" with a record of how the phone
was used over several months.

## The decision it should help with

Foldlytics is not meant to make people open the app more often or chase a daily
score. It is meant to help a foldable owner answer one question when it is time
to replace the phone:

> Should my next phone be a foldable too?

That decision depends on a few more specific questions:

1. What share of classified device use was on the inner display?
2. How many opens were detected on a typical observed day?
3. On how many recorded days was the inner display used at all?
4. Which apps were displayed on the inner screen?
5. Did inner display use fall after the device was no longer new?
6. Is the record complete enough to trust those answers?

## Design choices

### 1. Keep a long history

Travel, work, illness, or a new app can make one day unusual. Whether a
foldable is useful is better judged over weeks and months, so Foldlytics keeps
history and shows changes over periods ranging from weeks to a year or more.

### 2. Do not fill gaps with guesses

Android does not provide a perfect physical hinge counter or keep an unlimited,
lossless usage history. Restarts, delayed background work, missing display
configuration events, and permission changes can all leave gaps.

Foldlytics does not turn those gaps into estimated cover or inner display time.
A `COVER -> INNER` transition with continuous evidence counts as a detected
open. An `INNER -> COVER` transition with continuous evidence counts as a
detected close. If the boundary is unknown, it stays unknown.

This can leave blank periods in the record, but it avoids presenting estimates
as recorded use.

The exact definitions and limitations are in [MEASUREMENT.md](MEASUREMENT.md).

### 3. Store usage history on the device

App usage can reveal work patterns, interests, health concerns, relationships,
and routines. Foldlytics therefore keeps the history local:

- It does not require an account.
- It declares no network permission.
- It includes no advertising or analytics SDK.
- It does not send data to an external server automatically.
- It disables cloud backup of the local history.
- It exports or shares data only after an explicit user action.

I consider these requirements for keeping a long record of private app usage.

## What Foldlytics is not

- It is not simply a physical hinge counter.
- It is not a benchmark for comparing different owners.
- It does not judge productivity or digital wellbeing.
- It is not a parental control or employee monitoring tool.
- It is not a cloud analytics service.
- It does not assume that every Android device provides the same events.
- It does not reconstruct data that Android did not retain.

## What a useful result looks like

After several months to a year, a user should be able to describe their use
more precisely than "I think I used the large screen."

They may find that they used the inner display on most recorded days and can
name the apps they displayed there most often. They may see that inner display
use fell after the first month and decide that a conventional phone would suit
them better. The record may also contain too many gaps to decide yet, in which
case Foldlytics should make that limitation clear.

The app has done its job when the owner can choose their next phone using their
own usage history, even if the answer is not to buy another foldable.
