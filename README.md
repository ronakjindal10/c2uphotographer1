This Android app allows photographers to upload photos in real time so that event guests can get their photos. A listener is looking for new photos created in the directory /Nikon downloads of the phone's internal storage. It also looks for a watermark png file in the directory /Watermark, file name watermark.png. It uploads these photos to the /upload-photo endpoint hosted on Render currently (https://c2u-api.onrender.com/upload-photo).

Pending:
~~1. Sony app creates new folders abruptly for new photos. Need to scan all child folders for new photos to make this work reliably with Sony cameras.~~
~~2. Upload photos one at a time instead of multiple at once. Since mobile internet bandwidth is limited, trying to upload multiple photos at once may lead to timeout for all requests.~~
2. Implement autoscroll as per logs
~~3. Allow scrolling the app~~
~~3. Colour coding on logs: red for errors~~
~~4. Start processing on image creation completion not on creation~~
5. Check memory usage of app and optimise if needed
~~6. Handle portrait photos watermarking~~
7. Aggressive compression of photos after 2 failed attempts to make uploads work with low network speeds
8. ~~Handle Sony's file transfer system where CLOSE_WRITE event is triggered for temp file creation and then a MOVED_TO event is triggered for the actual file creation~~
~~9. Ability to read external storage on Android 13 to access photos created beyond this app's file scope~~
~~10. Choose file directory to monitor from the UI so that app doesn't need to be loaded from Android Studio~~
11. Show last photo uploaded ~~with file name and time~~
~~12. Log Photo Processor logs to the screen. Currently, it's only shown on Android studio logcat.~~
12. Stop retrying upload of failed photos after a while
~~13. Implement Foreground service to make the app processes more resilient~~ Need to test how resilient the service is
14. Some photos are getting uploaded twice. There are instances when the new file queue and retry queue have the same photos. Need to investigate.
~~15. Choose watermark file from the UI so that the photographer doesn't need to deal with watermark file names.~~
~~16. PhotoProcessor logs go back to gray colour when the next line is printed. Need to fix this.~~
17. Clean up the logs that are printed: show file names only instead of directories and remove unnecessary logs like showing failed twice ~~or logging the queues~~.


C2U product ideas:
1. The photographer can DM the edited photos to guests on WhatsApp building a connection with them for future business. The guests are asked for their phone number during sign up. The photographer uploads the edited photos later and we can use a WhatsApp extension on their browser to send photos on their behalf.
2. The guest is asked to review the photographer after downloading a few photos. The review may be on Google maps or any other platform the photographer wants.