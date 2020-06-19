const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// TODO: Add token to db on login
// TODO: IDs for notifications? Handle asynchronous distraction prediction

exports.sendNotification = functions.database.ref('/rooms/{roomId}/{messageId}')
	.onCreate((snap, context) => {
		console.log('Message: ', snap.val().content);

		const message = snap.val();
		const contactUid = message.to;
		const userId = message.from;
		const rmId = context.params.roomId;
		const msgId = context.params.messageId;

		return admin.database().ref(`/users/${contactUid}/deviceToken`).once('value').then(snapshot => {
			const deviceToken = snapshot.val();


			return admin.database().ref(`/users/${contactUid}/contacts/${userId}`).once('value').then(snap => {
				if (snap.val() !== null) {
					user = snap.val();
					const payload = {
						token: deviceToken,
						data: {
							roomId: rmId,
							messageId: msgId,
							fromUid: message.from,
							fromName: user.name,
							toUid: message.to,
							content: message.content,
							timestamp: String(message.serverTimestamp),
							distracting: String(message.distracting),
							icon: user.thumb
						}
					}
					return admin.messaging().send(payload)
						.then((response) => {
    						console.log('Successfully sent message:', response);
							console.log(`Sent to: ${deviceToken}`);
							return null;
						})
						.catch((e) => {
    						console.log('Error sending message:', e);
							return null;
						});
				} else {
					return admin.database().ref(`/users/${userId}`).once('value').then(snap => {
						const user = snap.val();

						const payload = {
							token: deviceToken,
							data: {
								roomId: rmId,
								messageId: msgId,
								fromUid: message.from,
								fromName: user.name,
								toUid: message.to,
								content: message.content,
								timestamp: String(message.serverTimestamp),
								distracting: String(message.distracting),
								icon: user.thumb
							}
						}
						return admin.messaging().send(payload)
							.then((response) => {
	    						console.log('Successfully sent message:', response);
								console.log(`Sent to: ${deviceToken}`);
								return null;
							})
							.catch((e) => {
	    						console.log('Error sending message:', e);
								return null;
							});
					});
				}
			});
		});
	});
