This Android app allows photographers to upload photos in real time so that event guests can get their photos. A listener is looking for new photos created in the directory /Nikon downloads of the phone's internal storage. It also looks for a watermark png file in the directory /Watermark, file name watermark.png. It uploads these photos to the /upload-photo endpoint hosted on Render currently (https://c2u-api.onrender.com/upload-photo).

Pending:
1. Upload photos one at a time instead of multiple at once. Since mobile internet bandwidth is limited, trying to upload multiple photos at once may lead to timeout for all requests.
2. Implement autoscroll as per logs
3. Allow scrolling the app
3. Colour coding on logs: red for errors
4. Start processing on image creation completion not on creation
5. Check memory usage of app and optimise if needed
6. Handle portrait photos watermarking